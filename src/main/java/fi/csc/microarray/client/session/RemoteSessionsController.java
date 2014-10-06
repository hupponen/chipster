package fi.csc.microarray.client.session;

public class RemoteSessionsController {

	private RemoteSessionsModel model;

	public RemoteSessionsController(RemoteSessionsModel model) {
		this.model = model;
	}

	public RemoteSessionsModel getModel() {
		return this.model;
	}

	public void remove(String string) {
		
	}

	public void init() {
		model.setItems(new String[] { "ses1", "ses2", "ses3" });
	}
}
