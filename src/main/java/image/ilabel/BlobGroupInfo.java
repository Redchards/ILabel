package image.ilabel;

import ij.blob.Blob;

public class BlobGroupInfo {
	private int perimeterLowerBound;
	private int perimeterUpperBound;
	private int size;
	
	private int numberOfChar;
	private int numberOfImg;
	
	public BlobGroupInfo(int perimeterLowerThreshold, int perimeterUpperThreshold, int size) {
		this.perimeterLowerBound = perimeterLowerThreshold;
		this.perimeterUpperBound = perimeterUpperThreshold;
		this.size = size;
		
		numberOfChar = 0;
		numberOfImg = 0;
	}

	public int getPerimeterLowerBound() {
		return perimeterLowerBound;
	}

	public void setPerimeterLowerBound(int perimeterLowerBound) {
		this.perimeterLowerBound = perimeterLowerBound;
	}

	public int getPerimeterUpperBound() {
		return perimeterUpperBound;
	}

	public void setPerimeterUpperBound(int perimeterUpperBound) {
		this.perimeterUpperBound = perimeterUpperBound;
	}

	public int size() {
		return size;
	}
	
	public void incrementSize(int offset) {
		size += offset;
	}
	
	public void decrementSize(int offset) {
		size -= offset;
		
		if(size < 0) size = 0;
	}
	
	public void incrementSize() {
		incrementSize(1);
	}
	
	public void decrementSize() {
		decrementSize(1);
	}
	
	public void incrementNumberOfChar(int offset) {
		numberOfChar += offset;
	}
	
	public void decrementNumberOfChar(int offset) {
		numberOfChar -= offset;
	}
	
	public void incrementNumberOfChar() {
		incrementNumberOfChar(1);
	}
	
	public void decrementNumberOfChar() {
		decrementNumberOfChar(1);
	}
	
	public void incrementNumberOfImg(int offset) {
		numberOfImg += offset;
	}
	
	public void decrementNumberOfImg(int offset) {
		numberOfImg -= offset;
	}
	
	public void incrementNumberOfImg() {
		incrementNumberOfImg(1);
	}
	
	public void decrementNumberOfImg() {
		decrementNumberOfImg(1);
	}
	
	public ComponentClass getMainComponentClass() {
		System.out.println("Number of char : " + numberOfChar);
		System.out.println("Number of img : " + numberOfImg);

		if(numberOfChar > numberOfImg) {
			return ComponentClass.CHARACTER;
		}
		else {
			return ComponentClass.IMAGE;
		}
	}
	
	public boolean isInGroup(Blob blob) {
		return isAlmostInGroup(blob, 0);
	}
	
	// Find a better method than doing "Math.floor"
	public boolean isAlmostInGroup(Blob blob, double epsilon) {
		return Math.floor(blob.getPerimeter()) >= perimeterLowerBound - epsilon && Math.floor(blob.getPerimeter()) <= perimeterUpperBound + epsilon;
	}
}
