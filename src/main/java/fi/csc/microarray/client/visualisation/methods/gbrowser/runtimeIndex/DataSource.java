package fi.csc.microarray.client.visualisation.methods.gbrowser.runtimeIndex;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * <p>One source of genomic content. Abstraction hides the physical data source, that can be either 
 * a file or a URL accessed via HTTP.</p>
 *
 * <p>TODO: add comparison methods so that when DataSource is used as a key
 * in a hash map, data sources with identical urls or file paths would be
 * considered the same.</p>
 * 
 * @author Petri Klemelä
 *
 */
public abstract class DataSource {

	protected File file = null;
	protected URL url = null;
	protected String name;

	public DataSource(URL url) throws FileNotFoundException, URISyntaxException {
		
		if (url != null) {
			if ("file".equals(url.getProtocol())) {
				file = new File(url.toURI());
			} else {
				this.url = url;
			}
			this.name = url.toString(); 
		}
	}
	
	public DataSource(URL urlRoot, String path)
	        throws FileNotFoundException, MalformedURLException, URISyntaxException {
		this(new URL(urlRoot.toString() + "/" + path));
	}
	
	@Override
	public String toString() {
		return name;
	}
}
