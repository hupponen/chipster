package fi.csc.microarray.client.session;

import java.util.List;

import javax.jms.JMSException;

import fi.csc.microarray.client.ClientApplication;
import fi.csc.microarray.filebroker.DbSession;

public class RemoteSessionsController implements SessionChangeListener {

	private RemoteSessionsModel model;
	private RemoteSessionsView view;
	private SessionManager sessionManager;
	
	private ClientApplication app;

	public RemoteSessionsController(SessionManager sessionManager, ClientApplication app) {
		this.app = app;
		this.sessionManager = sessionManager;
		sessionManager.addSessionChangeListener(this);
	}

	public void remove(DbSession dbSession) throws JMSException {
		sessionManager.removeRemoteSession(dbSession.getDataId());
	}

	public void setModel(RemoteSessionsModel model) throws JMSException {
		this.model = model;
		model.setListener(this);
		loadData();
	}

	public void setView(RemoteSessionsView view) {
		this.view = view;
		view.setController(this);
	}

	public void modelChanged() {
		view.modelChanged(model);
	}

	private void loadData() throws JMSException {
		List<DbSession> sessions = sessionManager.listRemoteSessions();
		model.setSessions(sessions);
	}

	public void close() {
		view.close();
		sessionManager.removeSessionsChangeListener(this);
	}

	@Override
	public void sessionRemoved(DbSession session) {
		model.removeSession(session);
	}

	public void open(DbSession session) throws Exception {
		sessionManager.loadStorageSession(session.getDataId());
	}

	public void save(String name) throws Exception {
		sessionManager.saveStorageSession(name);
	}

	public void reportException(Exception e) {
		app.reportException(e);
	}

	public void setCurrentDirectory(String dir) {
		model.setCurrentDirectory(dir);
	}
}
