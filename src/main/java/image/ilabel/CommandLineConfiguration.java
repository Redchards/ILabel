package image.ilabel;

import com.beust.jcommander.Parameter;

public class CommandLineConfiguration {
	
	@Parameter(names = {"-t", "--threshold"}, description = "The threshold to be used for binarization (integer value)")
	public int threshold;
	
	@Parameter(names = {"-f", "--file"}, description = "The file to be processed.")
	public String file;
}
