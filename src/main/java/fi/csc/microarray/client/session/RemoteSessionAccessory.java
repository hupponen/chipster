package fi.csc.microarray.client.session;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.MalformedURLException;
import java.util.List;

import javax.jms.JMSException;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;

import net.miginfocom.swing.MigLayout;
import fi.csc.microarray.client.SwingClientApplication;
import fi.csc.microarray.client.operation.ColoredCircleIcon;
import fi.csc.microarray.client.serverfiles.ServerFile;
import fi.csc.microarray.client.serverfiles.ServerFileSystemView;
import fi.csc.microarray.constants.VisualConstants;
import fi.csc.microarray.filebroker.DbSession;
import fi.csc.microarray.messaging.admin.StorageAdminAPI.StorageEntryMessageListener;
import fi.csc.microarray.messaging.admin.StorageEntry;
import fi.csc.microarray.util.Strings;

public class RemoteSessionAccessory extends JPanel implements ActionListener, PropertyChangeListener {

	private static final Color LOW_DISK_USAGE_COLOR = VisualConstants.COLOR_BLUE_GREEN;
	private static final Color HIGH_DISK_USAGE_COLOR = VisualConstants.COLOR_ORANGE;
	
	private long LOW_LIMIT = 10_000_000_000l;
	private long HIGH_LIMIT = 100_000_000_000l;
	
	private String HUMAN_READABLE_LOW_LIMIT = Strings.toHumanReadable(LOW_LIMIT).trim() + "B";
	private String HUMAN_READABLE_HIGH_LIMIT = Strings.toHumanReadable(HIGH_LIMIT  ).trim() + "B";

	private String disclaimer = ""
			+ "Storage here is for working copies and may not be backed up. Store "
			+ "another copy of all your valuable data elsewhere.";
	
	private String lowDiskUsage = ""
			+ "Store up to " + HUMAN_READABLE_LOW_LIMIT + " as long as you want.";

	private String highDiskUsage = ""
			+ "Store up to " + HUMAN_READABLE_HIGH_LIMIT + ", but please remove "
			+ "your data when you aren't anymore actively working on it.";
	
//	private String highDiskUsage = ""
//			+ "Store up to " + HUMAN_READABLE_HIGH_LIMIT + " when you are "
//			+ "actively working on it. We'll remind you by email if you "
//			+ "haven't used your data lately.";

	private JPanel panel = new JPanel();
	private JLabel manageTitle = new JLabel("Selected session");
	private JLabel diskUsageTitle = new JLabel("Disk usage");
	private JLabel disclaimerTitle = new JLabel("No backups");
	private JButton removeButton = new JButton("Remove");
	private JProgressBar quotaBar = new JProgressBar();
	private JTextArea disclaimerText = new JTextArea(disclaimer);
	private JLabel lowDiskUsageIcon = new JLabel(new ColoredCircleIcon(LOW_DISK_USAGE_COLOR));
	private JLabel highDiskUsageIcon = new JLabel(new ColoredCircleIcon(HIGH_DISK_USAGE_COLOR));	
	private JTextArea lowDiskUsageText = new JTextArea(lowDiskUsage);
	private JTextArea highDiskUsageText = new JTextArea(highDiskUsage);
	private JFileChooser fileChooser;
	private SessionManager sessionManager;
	private SwingClientApplication app;

	public RemoteSessionAccessory(JFileChooser fileChooser, SessionManager sessionManager, SwingClientApplication app) {
		
		this.fileChooser = fileChooser;
		this.sessionManager = sessionManager;
		this.app = app;
		
		fileChooser.addPropertyChangeListener(this);		
		
		panel.setLayout(new MigLayout("", "[fill]", ""));
		panel.setBackground(Color.white);
		panel.setBorder(new LineBorder(VisualConstants.TOOL_LIST_BORDER_COLOR));		

		removeButton.addActionListener(this);
		
		disclaimerText.setLineWrap(true);
		lowDiskUsageText.setLineWrap(true);
		highDiskUsageText.setLineWrap(true);
		
		disclaimerText.setWrapStyleWord(true);
		lowDiskUsageText.setWrapStyleWord(true);
		highDiskUsageText.setWrapStyleWord(true);
		
		disclaimerText.setEditable(false);
		lowDiskUsageText.setEditable(false);
		highDiskUsageText.setEditable(false);
		
		disclaimerText.setOpaque(false);
		lowDiskUsageText.setOpaque(false);
		highDiskUsageText.setOpaque(false);
		
		quotaBar.setStringPainted(true);
		quotaBar.setBackground(Color.white);
		// get rid of a blue shadow by removing the border of the JProggresBar
		// and using JPanel to show the border
		quotaBar.setBorderPainted(false);
		quotaBar.setBorder(null);
		JPanel quotaPanel = new JPanel(new MigLayout("fill, gap 0!, insets 0"));
		quotaPanel.setBorder(new LineBorder(Color.GRAY));
		quotaPanel.add(quotaBar, "growx, width 300px");
		
		manageTitle.setFont(UIManager.getFont("TitledBorder.font"));
		diskUsageTitle.setFont(UIManager.getFont("TitledBorder.font"));
		disclaimerTitle.setFont(UIManager.getFont("TitledBorder.font"));
		
		manageTitle.setForeground(UIManager.getColor("TitledBorder.titleColor"));
		diskUsageTitle.setForeground(UIManager.getColor("TitledBorder.titleColor"));		
		disclaimerTitle.setForeground(UIManager.getColor("TitledBorder.titleColor"));

		panel.add(manageTitle, "span, wrap");
		panel.add(removeButton, "sizegroup actions, skip, growx 0, wrap");
		
		panel.add(diskUsageTitle, "span, wrap");
		panel.add(quotaPanel, "skip, wrap");
		panel.add(lowDiskUsageIcon, "aligny top, gap top 5");
		panel.add(lowDiskUsageText, "wrap");
		panel.add(highDiskUsageIcon, "aligny top, gap top 5");
		panel.add(highDiskUsageText, "wrap");
				
		panel.add(disclaimerTitle, "span, wrap");
		panel.add(disclaimerText, "skip, wrap");
		
		this.setLayout(new MigLayout("fill, insets 0 0 1 1"));
		this.add(panel, "grow");

		update();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == removeButton) {
			removeSelectedSession();
		} 
	}

	private void removeSelectedSession() {
		
		String sessionUuid = null;
		
		try {
			sessionUuid = getSelectedSession();
			
			if (sessionUuid == null) {
				throw new RuntimeException("session not found");
			}
		} catch (Exception e) {
			throw new RuntimeException("internal error: URL or name from save dialog was invalid"); // should never happen
		}
		
		try {
			// remove selected session
			if (sessionManager.removeRemoteSession(sessionUuid)) {			
				
				update();
			}

		} catch (JMSException e) {
			app.reportException(e);
		}
	}

	private String getSelectedSession() throws MalformedURLException {
		File selectedFile = fileChooser.getSelectedFile();
		if (selectedFile == null) {
			return null;
		}
		String filename = selectedFile.getPath().substring(ServerFile.SERVER_SESSION_ROOT_FOLDER.length()+1);
		@SuppressWarnings("unchecked")
		List<DbSession> sessions = (List<DbSession>)fileChooser.getClientProperty("sessions");
		return sessionManager.getSessionUuid(sessions, filename);
	}

	private void update() {
		try {
			ServerFileSystemView view = RemoteSessionChooserFactory.updateRemoteSessions(sessionManager, fileChooser);
			
			// trigger fileChooser to update its session list 
			fileChooser.setCurrentDirectory(null);
			fileChooser.setCurrentDirectory(view.getRoot());

			StorageEntryMessageListener reply = sessionManager.getStorageUsage();
			
			long quota = reply.getQuota();
			long diskUsage = 0l;
			
			for (StorageEntry entry : reply.getEntries()) {
				diskUsage += entry.getSize();
			}
			quotaBar.setMaximum((int) (quota / 1024 / 1024));
			quotaBar.setValue((int) (diskUsage / 1024 / 1024));
			String humanReadableDiskUsage = Strings.toHumanReadable(diskUsage, true, true);
			String humanReadableQuota = Strings.toHumanReadable(quota, true, true);
			quotaBar.setString("Disk usage: " + humanReadableDiskUsage + "B / " +humanReadableQuota + "B");
			if (diskUsage < LOW_LIMIT) {
				quotaBar.setForeground(LOW_DISK_USAGE_COLOR);
			} else {
				quotaBar.setForeground(HIGH_DISK_USAGE_COLOR);
			}

		} catch (MalformedURLException | JMSException | InterruptedException e) {
			app.reportException(e);
		}		
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {

		if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY
				.equals(evt.getPropertyName())) {

			try {
				removeButton.setEnabled(getSelectedSession() != null);
			} catch (MalformedURLException e) {
				app.reportException(e);
			}
		} 
//		else if (JFileChooser.SELECTED_FILES_CHANGED_PROPERTY.equals(
//				evt.getPropertyName())) {
//
//			File[] files = fileChooser.getSelectedFiles();
//		}		    
	}
}
