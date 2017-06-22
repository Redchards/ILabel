package image.ilabel;

import com.beust.jcommander.JCommander;

public class ApplicationConfiguration {
	
	private JCommander cmdParser;
	
	private String file;
	private int threshold;
	
	public static final int DEFAULT_BINARIZATION_THRESHOLD = 10;

	
	public ApplicationConfiguration(String[] args) throws InvalidCommandLineException {
		CommandLineConfiguration cmdConfig = new CommandLineConfiguration();
		
		cmdParser = new JCommander(cmdConfig, args);
		
		file = cmdConfig.file;
		
		if(file == null) {
			throw new InvalidCommandLineException("No file defined as input !!");
		}
		
		threshold = cmdConfig.threshold;
		
		if(threshold <= 0) {
			threshold = DEFAULT_BINARIZATION_THRESHOLD;
		}
	}

	public String getFile() {
		return file;
	}

	public int getThreshold() {
		return threshold;
	}
}
