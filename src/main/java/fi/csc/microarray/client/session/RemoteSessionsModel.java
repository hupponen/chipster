package fi.csc.microarray.client.session;

public class RemoteSessionsModel {

	private String[] items;
	private Object view;
	private RemoteSessionsView listener;

	public String[] getItems() {
		return items;
	}

	public int getDiskUsage() {
		return 500_000_000;
	}

	public int getMaxQuota() {
		return 1_000_000_000;
	}

	public void setItems(String[] items) {
		this.items = items;
		listener.modelChanged();
	}

	public void setListener(RemoteSessionsView remoteSessionsView) {
		this.listener = remoteSessionsView;
	}
}
