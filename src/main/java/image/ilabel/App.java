package image.ilabel;

import ij.ImagePlus;
import ij.io.Opener;
import ij.process.ImageProcessor;

/**
 * Hello world!
 *
 */
public class App 
{
	
	
    public static void main( String[] args )
    {
    	try {
			ApplicationConfiguration config = new ApplicationConfiguration(args);
	    	
	    	Opener opener = new Opener();  
	    	String imageFilePath = config.getFile();
	    	ImagePlus imp = opener.openImage(imageFilePath);
	    	ImageProcessor ip = imp.getProcessor();

	    	Labelizer labelizer = new Labelizer(imp, config.getThreshold());
	    	labelizer.setup(null, imp);
	    	labelizer.run(ip);
		} catch (InvalidCommandLineException e) {
			System.out.println("Error in the command line :");
			e.printStackTrace();
		}

    }
}
