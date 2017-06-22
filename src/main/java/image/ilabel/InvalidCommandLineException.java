package image.ilabel;

public class InvalidCommandLineException extends Exception {
	/**
	 * Generated UID.
	 */
	private static final long serialVersionUID = 120761124049688773L;

	public InvalidCommandLineException(String msg) {
		super(msg + " Use --help to get more informations.");
	}
}
