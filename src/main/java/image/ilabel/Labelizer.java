package image.ilabel;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.color.ColorSpace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.PolygonFiller;
import ij.blob.Blob;
import ij.blob.ManyBlobs;
import ij.gui.ImageRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.io.FileSaver;

// TODO : Size heuristic :
// We should process every blob and check the most common sizes.
// We also need to delete everything with a width or height of 0 (and maybe even 1)
// TODO : create a function to binarize the image.
public class Labelizer implements PlugInFilter {
	
	//private EnumMap<ComponentClass, Blob> classifiedConnectedComponents;
	//private EnumMap<ComponentClass, Blob> temporarlyClassifiedConnectedComponents;
	
	private HashMap<Blob, ComponentClass> temporarlyClassifiedConnectedComponents;
	private HashMap<Blob, ComponentClass> classifiedConnectedComponents;
	
	private ManyBlobs allBlobs;
	private ImagePlus processedImage;
	
	
	private static final int DEFAULT_IMAGE_WIDTH = 500;
	private static final int DEFAULT_IMAGE_HEIGHT = 500;
	
	// Some of these values should be adjusted according to the image size !
	private static final double PIXEL_DENSITY_THRESHOLD = 0.75;
	private static final int DEFAULT_SPECIAL_LETTER_DELTA_X = 5;
	private static final int DEFAULT_SPECIAL_LETTER_DELTA_Y = 8;
	private static final double GROUP_SIZE_MAIN_DIVIDER = 20;
	private static final double SMALL_GROUP_THRESHOLD = 1;
	private static final int DEFAULT_GROUP_MAX_HOLES = 8;
	private static final int BLOB_GREAT_SIZE_DIFF_DELTA = 120;
	
	private static final int NOISE_REMOVE_WIDTH_THRESHOLD = 1;
	private static final int NOISE_REMOVE_HEIGHT_THRESHOLD = 1;
	
	private static final double CLOSURE_EFFECT_THRESHOLD = 0.035;
	private static final double CLOSURE_EFFECT_LIMIT_OFFSET = 0.01;
	
	private int groupMaxHoles;
	private int binarizationThreshold;
	private int specialLetterDeltaX;
	private int specialLetterDeltaY;
	private int blobSizeDelta;

	public Labelizer(ImagePlus imp) {
		this(imp, imp.getProcessor().getAutoThreshold());
	}
	
	public Labelizer(ImagePlus imp, int binarizationThreshold) {
		this.binarizationThreshold = binarizationThreshold;
		
		double widthScaling = imp.getWidth() / (double)DEFAULT_IMAGE_WIDTH;
		double heightScaling = imp.getHeight() / (double)DEFAULT_IMAGE_HEIGHT;
		
		double totalScaling = widthScaling + heightScaling;
		double amortizedScaling = Math.pow(totalScaling, 1.0/2);
		
		groupMaxHoles = (int)Math.floor(DEFAULT_GROUP_MAX_HOLES * totalScaling);
		System.out.println(groupMaxHoles);
		specialLetterDeltaX = (int)Math.floor((DEFAULT_SPECIAL_LETTER_DELTA_X * amortizedScaling));
		specialLetterDeltaY = (int)Math.floor((DEFAULT_SPECIAL_LETTER_DELTA_Y * amortizedScaling));
		blobSizeDelta = (int)Math.floor((BLOB_GREAT_SIZE_DIFF_DELTA * amortizedScaling));
	}

	public boolean isSpecialChar(Blob blob) {
		Rectangle currentPoints = blob.getOuterContour().getBounds();
		
		// System.out.println("" + currentPoints.getWidth() + " : " + currentPoints.getHeight());
		
		return (currentPoints.getHeight() / currentPoints.getWidth() >= 1.75);
	}
	
	public double computeBlobBinaryDensity(Blob blob, int threshold) {
		return computeImageBinaryDensity(Blob.generateBlobImage(blob), threshold);
	}
	
	public double computeImageBinaryDensity(ImagePlus img, int threshold) {
		ImageProcessor proc = img.getProcessor();
		
		proc.invert();
		proc.threshold(threshold);
		proc.invert();
		
		// new ImagePlus("Labeled image", proc).show();
		
		ImageStatistics stats = img.getStatistics();
		
		if(stats.pixelCount != 0) {
			return (double)stats.getHistogram()[0] / stats.pixelCount;
		}
		return 0.0;
	}
	
	public List<BlobGroupInfo> getBlobGroupInfo() {
		
		HashMap<Integer, List<Blob>> perimeterBlobMap = new HashMap<>();
		
		for(Blob blob : temporarlyClassifiedConnectedComponents.keySet()) {
			if(!perimeterBlobMap.containsKey(blob.getPerimeter())) {
				List<Blob> blobList = new ArrayList<>();
				blobList.add(blob);
				perimeterBlobMap.put((int)Math.floor(blob.getPerimeter()), blobList);
			}
			else {
				perimeterBlobMap.get((int)Math.floor(blob.getPerimeter())).add(blob);
			}
		}

		boolean inBlobGroup = false;
		int currentHoles = 0;
		
		List<BlobGroupInfo> blobGroupList = new ArrayList<>();
		System.out.println("max = " + Collections.max(perimeterBlobMap.keySet()));
		for(int i = 0; i <= Collections.max(perimeterBlobMap.keySet()); i++) {
			
			BlobGroupInfo currentGroupInfo = null;

			if(perimeterBlobMap.containsKey(i)) {
				if(!inBlobGroup) {
					currentGroupInfo = new BlobGroupInfo(i, i, perimeterBlobMap.get(i).size());
					blobGroupList.add(currentGroupInfo);
					System.out.println("Add for " + i);
					inBlobGroup = true;
				}
				else {
					currentGroupInfo = blobGroupList.get(blobGroupList.size() - 1);
					currentGroupInfo.incrementSize(perimeterBlobMap.get(i).size());
					currentGroupInfo.setPerimeterUpperBound(i);
				}
				for(Blob blob : perimeterBlobMap.get(i)) {
					if(temporarlyClassifiedConnectedComponents.get(blob) == ComponentClass.CHARACTER) {
						currentGroupInfo.incrementNumberOfChar();
					}
					else {
						currentGroupInfo.incrementNumberOfImg();
					}
				}
			}
			else {
				if(currentHoles <= groupMaxHoles) {
					currentHoles++;
				}
				else {
					inBlobGroup = false;
					currentHoles = 0;
				}
			}
		}
		
		return blobGroupList;
	}
	
	// Maybe call it "blob group heuristic" ?
	public void blobSizeHeuristic(List<BlobGroupInfo> groupInfo, int mainGroupIndex, Blob blob) {
		if(temporarlyClassifiedConnectedComponents.get(blob) == ComponentClass.IMAGE) {
			if(groupInfo.get(mainGroupIndex).isInGroup(blob)) {
				temporarlyClassifiedConnectedComponents.put(blob, ComponentClass.CHARACTER);
			}
			else {
				for(int i  = 0; i < groupInfo.size(); i++) {
					if(groupInfo.get(i).isInGroup(blob) && (double)groupInfo.get(i).size() >= groupInfo.get(mainGroupIndex).size() / GROUP_SIZE_MAIN_DIVIDER) {
						temporarlyClassifiedConnectedComponents.put(blob, ComponentClass.CHARACTER);
					}
				}
			}
		}
		else {
			for(int i = groupInfo.size() - 1; i >= 0; i--) {
				//if(blob.getPerimeter() > 200) System.out.println(blob.getPerimeter() + " : " + groupInfo.get(i).getPerimeterLowerBound() + " : " + groupInfo.get(i).size()+ " : " + groupInfo.get(i).isInGroup(blob));

				if(groupInfo.get(i).isInGroup(blob)) {
					if(groupInfo.get(i).getMainComponentClass() == ComponentClass.IMAGE 
					|| groupInfo.get(i).getPerimeterLowerBound() >= groupInfo.get(mainGroupIndex).getPerimeterUpperBound() + blobSizeDelta) {
						//System.out.println("Blob is in group");
						temporarlyClassifiedConnectedComponents.put(blob, ComponentClass.IMAGE);
					}
					else {
						break;
					}
				}
			}
		}
	}
	
	public void closureHeuristic(Blob blob) {
		double originalDensity = computeBlobBinaryDensity(blob, binarizationThreshold);
		
		ImagePlus img = Blob.generateBlobImage(blob);
		ImageProcessor proc = img.getProcessor();
		
		proc.invert();
		proc.threshold(binarizationThreshold);
		proc.invert();

		proc.dilate();
		proc.erode();
		
		double newDensity = computeImageBinaryDensity(new ImagePlus("Image after binary opening", proc), binarizationThreshold);
		
		System.out.println("orig : " + originalDensity);
		System.out.println("new : " + newDensity);
		
		if((newDensity - originalDensity) >= CLOSURE_EFFECT_THRESHOLD || (newDensity >= 1.0 && originalDensity + (CLOSURE_EFFECT_THRESHOLD - CLOSURE_EFFECT_LIMIT_OFFSET) < 1.0)) {
			temporarlyClassifiedConnectedComponents.put(blob, ComponentClass.CHARACTER);
		}
	}

	public void run(ImageProcessor proc) {
		// proc = proc.convertToByte(true);
		
		/* proc.invert();
		proc.threshold(10);
		proc.invert();*/
		
	    allBlobs.findConnectedComponents(); // Start the Connected Component Algorithm
	    
	    for(Blob blob : allBlobs) {
	    	temporarlyClassifiedConnectedComponents.put(blob, ComponentClass.CHARACTER);
	    }
	    
	    // We want to extract the characters like "i", "j".
	    //ManyBlobs filteredBlobs = allBlobs.filterBlobs(1, 64, "getPerimeter");
	    int nextIndex = allBlobs.size();
	    
	    // Need to filter allBlobs using a coordinate method.
	    // TODO : FILTER !  (DO NOT USE allBlobs.filterBlobs !!!!!!)
	    for(int i = 0; i < allBlobs.size(); i++) {
	    	Blob current = allBlobs.get(i);
	    	if(current.getOuterContour() == null){ System.out.println("Failed"); continue;}
	    	//System.out.println("Try " + current.getOuterContour().getBounds().getX() + " : " + current.getOuterContour().getBounds().getY());
	    	
	    	for(int j = i; j < allBlobs.size(); j++) {
	    		Rectangle currentPoints = allBlobs.get(i).getOuterContour().getBounds();
	    		/*int height = (int)currentPoints.getHeight();
				int width = (int)currentPoints.getWidth();
				proc.setColor(Color.RED);*/
				//proc.drawRect((int)currentPoints.getX(), (int)currentPoints.getY(), width, height);
	    		Blob candidate = allBlobs.get(j);
	    		Rectangle candidatePoints = candidate.getOuterContour().getBounds();

	    		if(currentPoints != null && candidatePoints != null 
	    				&& currentPoints.getX() + currentPoints.getWidth() <= candidatePoints.getX() + candidatePoints.getWidth() + specialLetterDeltaX) {
	    			if(currentPoints.getX() >= candidatePoints.getX() - specialLetterDeltaX 
	    					&& candidatePoints.getY() > currentPoints.getY() 
	    					&& candidatePoints.getY() < currentPoints.getY() + specialLetterDeltaY
	    					&& candidatePoints.getHeight() > currentPoints.getHeight()
	    					&& candidatePoints.getY() > currentPoints.getY() + currentPoints.getHeight()) {
    					//System.out.println("Density : " + computeBlobBinaryDensity(candidate, BINARIZATION_THRESHOLD));
    					// TODO : Bug in the case of "li" for example, the bottom i will be taken by the l"
	    				if(isSpecialChar(candidate)) { // && computeBinaryDensity(candidate, binarizationThreshold) >= PIXEL_DENSITY_THRESHOLD) {
	    					//System.out.println("Detect a special char at " + candidatePoints[3]);
	    					//proc.drawRect((int)currentPoints.getX(), (int)currentPoints.getY(), (int)candidatePoints.getWidth(), (int)candidatePoints.getHeight() + (int)(candidatePoints.getY() - currentPoints.getY()));
	    					
	    					Polygon newBounds = new Polygon();
	    					
	    					//System.out.println("Found at " + candidatePoints.getX() + " : " + candidatePoints.getY());
	    					// Should take min(currentPoints.getX(), candidatePoints.getX());
	    					// Same for the width
	    					int xCoord = (int) Math.min(currentPoints.getX(), candidatePoints.getX());
	    					int width = (int) Math.max(currentPoints.getWidth(), candidatePoints.getWidth());
	    					newBounds.addPoint(xCoord, (int)currentPoints.getY());
	    					newBounds.addPoint(xCoord + width + 1, (int)currentPoints.getY());
	    					newBounds.addPoint(xCoord + width + 1, (int)currentPoints.getY() + (int)candidatePoints.getHeight() + (int)(candidatePoints.getY() - currentPoints.getY()) + 1);
	    					newBounds.addPoint(xCoord, (int)currentPoints.getY() + (int)candidatePoints.getHeight() + (int)(candidatePoints.getY() - currentPoints.getY()) + 1);
	    					
	    					Blob newBlob = new Blob(newBounds, nextIndex);
	    					
	    					classifiedConnectedComponents.put(newBlob, ComponentClass.CHARACTER);
	    					temporarlyClassifiedConnectedComponents.remove(current);
	    					temporarlyClassifiedConnectedComponents.remove(candidate);

	    					//System.out.println(allBlobs.size());
	    					allBlobs.remove(current);
	    					allBlobs.remove(candidate);
	    					//System.out.println("Removed : " + candidatePoints.getX() + " : " + candidatePoints.getY());

	    					// allBlobs.add(newBlob);
	    					++nextIndex;
	    					break;
	    				}
	    			}
	    		}
	    	}
	    }
	    
	    // Attempt to remove the noise
	    // TODO : should scale with the image.
	    for(Blob blob : allBlobs) {
    		Rectangle currentPoints = blob.getOuterContour().getBounds();
    		if(currentPoints.getWidth() <= NOISE_REMOVE_WIDTH_THRESHOLD || currentPoints.getHeight() <= NOISE_REMOVE_HEIGHT_THRESHOLD) {
    			temporarlyClassifiedConnectedComponents.remove(blob);
    		}
	    }
	    
	    // First heuristic : pixel density
	    for(HashMap.Entry<Blob, ComponentClass> classifiedEntry : temporarlyClassifiedConnectedComponents.entrySet()) {
	    	if(computeBlobBinaryDensity(classifiedEntry.getKey(), binarizationThreshold) > PIXEL_DENSITY_THRESHOLD) {
	    		//System.out.println("Found image !");
	    		temporarlyClassifiedConnectedComponents.put(classifiedEntry.getKey(),  ComponentClass.IMAGE);
	    	}
	    }
	    
	    // Second heuristic : perform a binary opening.
	    for(Blob blob : temporarlyClassifiedConnectedComponents.keySet()) {
	    	if(temporarlyClassifiedConnectedComponents.get(blob) == ComponentClass.IMAGE) {
	    		closureHeuristic(blob);
	    	}
	    }
	    
	    // Third heuristic : blob class (size)
	    List<BlobGroupInfo> groupInfoList = getBlobGroupInfo();
	    
	    // Look for the main group (the biggest, in general the characters).
	    int maxGroupSize = 0;
	    int mainGroupIndex = -1;
	    for(int i = 0; i < groupInfoList.size(); i++) {
	    	if(groupInfoList.get(i).size() > maxGroupSize) {
	    		mainGroupIndex = i;
	    		maxGroupSize = groupInfoList.get(i).size();
	    	}
	    }


	    
	    for(Blob blob : temporarlyClassifiedConnectedComponents.keySet()) {
	    	//if(temporarlyClassifiedConnectedComponents.get(blob) == ComponentClass.IMAGE) System.out.println(blob.getPerimeter());
	    	blobSizeHeuristic(groupInfoList, mainGroupIndex, blob);
	    }
	    
		for(HashMap.Entry<Blob, ComponentClass> classifiedEntry : temporarlyClassifiedConnectedComponents.entrySet()) {
			classifiedConnectedComponents.put(classifiedEntry.getKey(), classifiedEntry.getValue());
		}
	    
		int classChar = 0;
		int classImg = 0;
	    for(HashMap.Entry<Blob, ComponentClass> classifiedEntry : classifiedConnectedComponents.entrySet()) {
	    	Blob current = classifiedEntry.getKey();
    		Rectangle currentPoints = current.getOuterContour().getBounds();
    		
    		if(classifiedEntry.getValue() == ComponentClass.CHARACTER) {
    			proc.setColor(Color.RED);
        		proc.setLineWidth(1);
        		classChar++;

    			//proc.drawRect((int)currentPoints.getX(), (int)currentPoints.getY(), (int)currentPoints.getWidth() + 1, (int)currentPoints.getHeight() + 1);
    		}
    		else {
    			proc.setColor(Color.BLUE);
        		proc.setLineWidth(5);
        		classImg++;

    			/*new PolygonFiller().fill(proc, currentPoints);
    			proc.setRoi(currentPoints);
    			proc.fill();*/
    		}
   			proc.drawRect((int)currentPoints.getX(), (int)currentPoints.getY(), (int)currentPoints.getWidth() + 1, (int)currentPoints.getHeight() + 1);

	    }
	    
	    System.out.println("Classified as character : " + classChar);
	    System.out.println("Classified as image : " + classImg);
	    
	    // new FileSaver(allBlobs.getLabeledImage()).saveAsBmp();
	    // new FileSaver(proc.createImage()).saveAsBmp();
	    new ImagePlus("Labeled image", proc).show();
	    new FileSaver(new ImagePlus("Labeled image", proc)).saveAsJpeg();
	    /*Frame frame = WindowManager.getFrame("ROI Manager");
	    if (frame == null)
	        IJ.run("ROI Manager...");
	    frame = WindowManager.getFrame("ROI Manager");
	    RoiManager roiManager = (RoiManager) frame;
	    
	    for (int i = 0; i < allBlobs.size(); i++) {
	        Polygon p = allBlobs.get(i).getOuterContour();
	        int n = p.npoints;
	        float[] x = new float[p.npoints];
	        float[] y = new float[p.npoints];   
	        for (int j=0; j<n; j++) {
	            x[j] = p.xpoints[j]+0.5f;
	            y[j] = p.ypoints[j]+0.5f;
	        }
	        Roi roi = new PolygonRoi(x,y,n,Roi.TRACED_ROI);             
	        Roi.setColor(Color.green);
	        roiManager.add(processedImage, roi, i);
	    }*/
	}

	// TODO : Add adjusting method to find the good threshold
	@Override
	public int setup(String arg0, ImagePlus img) {
		processedImage = img;
		temporarlyClassifiedConnectedComponents = new HashMap<>();
		classifiedConnectedComponents = new HashMap<>();
				
		new ImageConverter(img).convertToGray8();
		
		ImageProcessor proc = img.getProcessor();
		
		proc.invert();
		// binarizationThreshold = proc.getAutoThreshold();
		proc.threshold(binarizationThreshold);
		proc.invert();
		// proc.drawRect(0, 0, 25, 100);
		System.out.println(img.getNChannels());
		System.out.println(img.getBitDepth());
		
		ImageStatistics stats = img.getProcessor().getStatistics();
		System.out.println((stats.histogram[0] + stats.histogram[255]));
		System.out.println(stats.pixelCount);
		//new FileSaver(img).saveAsBmp();
		allBlobs = new ManyBlobs(img); // Extended ArrayList
		
		return 0;
	}

}
