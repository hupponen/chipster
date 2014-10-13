package fi.csc.microarray.client.session;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;

import net.miginfocom.swing.MigLayout;
import fi.csc.microarray.client.operation.ColoredCircleIcon;
import fi.csc.microarray.constants.VisualConstants;
import fi.csc.microarray.filebroker.DbSession;
import fi.csc.microarray.util.Strings;

public class RemoteSessionsView implements ActionListener {

	private static final Color LOW_DISK_USAGE_COLOR = VisualConstants.COLOR_BLUE_GREEN;
	private static final Color HIGH_DISK_USAGE_COLOR = VisualConstants.COLOR_ORANGE;

	private String disclaimer = ""
			+ "This server may not have any backups. Store another "
			+ "copy of all your valuable data in "
			+ "some secure place. Due to errors in hardware, software or "
			+ "administration all the data stored here may be lost.";
	
	private String lowDiskUsage = ""
			+ "You are free to store up to 10 GB as long as you want. ";

	private String highDiskUsage = ""
			+ "You can work with sessions up to 100 GB in size, but please remove the data "
			+ "when you aren't anymore actively working on it. We'll remind you by email "
			+ "when you have stored more than 10 GB and haven't used your data lately.";

	private JDialog frame;
	private JPanel panel = new JPanel(new MigLayout("wrap 4", "[fill]", ""));
	private JLabel policyLabel = new JLabel("Disk usage policy");
	private JLabel disclaimerLabel = new JLabel("No backups");
	private JButton removeButton = new JButton("Remove");
	private JButton closeButton = new JButton("Close");
	private JButton openButton = new JButton("Open");
	private JButton saveButton = new JButton("Save");
	private JProgressBar quotaBar = new JProgressBar();
	private JTextArea disclaimerText = new JTextArea(disclaimer);
	private JLabel lowDiskUsageIcon = new JLabel(new ColoredCircleIcon(LOW_DISK_USAGE_COLOR));
	private JLabel highDiskUsageIcon = new JLabel(new ColoredCircleIcon(HIGH_DISK_USAGE_COLOR));	
	private JTextArea lowDiskUsageText = new JTextArea(lowDiskUsage);
	private JTextArea highDiskUsageText = new JTextArea(highDiskUsage);
	private JLabel nameLabel = new JLabel("Sesison name: ");
	private JTextField nameTextArea = new JTextField("Session name");
	private JList<DbSession> list = new JList<DbSession>();
	private RemoteSessionsController controller;	

	public RemoteSessionsView(JFrame mainFrame) {
		
		frame = new JDialog(mainFrame);

		removeButton.addActionListener(this);
		closeButton.addActionListener(this);
		list.addMouseListener(new MouseAdapter() {
				
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON1 && 
						e.getClickCount() == 2) {
					controller.setCurrentDirectory(list.getSelectedValue().getName());
				}
			}
		});
		
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
		// get rid of a blue shadow by removing the border of the JProggresBar
		// and using JPanel to show the border
		quotaBar.setBorderPainted(false);
		quotaBar.setBorder(null);
		JPanel quotaPanel = new JPanel(new MigLayout("fill, gap 0!, insets 0"));
		quotaPanel.setBorder(new LineBorder(Color.GRAY));
		
		policyLabel.setFont(UIManager.getFont("TitledBorder.font"));
		policyLabel.setForeground(UIManager.getColor("TitledBorder.titleColor"));
		
		disclaimerLabel.setFont(UIManager.getFont("TitledBorder.font"));
		disclaimerLabel.setForeground(UIManager.getColor("TitledBorder.titleColor"));

		list.setCellRenderer(new DbSessionRenderer());
		list.setBorder(new LineBorder(Color.gray));

		panel.add(policyLabel, "wrap");
		panel.add(lowDiskUsageIcon, "split 2, spanx 2, aligny top, gap top 5");
		panel.add(lowDiskUsageText, "width 50%");
		panel.add(highDiskUsageIcon, "split 2, spanx 2, aligny top, gap top 5");
		panel.add(highDiskUsageText, "width 50%");
		quotaPanel.add(quotaBar, "growx");
		panel.add(quotaPanel, "spanx 4");
		panel.add(list, "growy, pushy, spanx 3");
		
		panel.add(removeButton, "wrap");
		
		boolean save = true;
		if (save ) {
			panel.add(nameLabel);
			panel.add(nameTextArea, "spanx 3");
			panel.add(disclaimerLabel, "wrap");
			panel.add(disclaimerText, "spanx 4, wrap");
			panel.add(saveButton, "skip 2, width 25%");
		} else {
			panel.add(openButton, "skip 2, width 25%");
		}
		panel.add(closeButton, "width 25%");
		
		if (save) {
			frame.setTitle("Save session");
		} else {
			frame.setTitle("Cloud sessions");
		}		

		frame.add(panel);
		frame.setSize(new Dimension(640, 480));
		frame.setVisible(true);

		frame.addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosing(WindowEvent e) {
				controller.close();
			}
		});
	}
	
	public void close() {
		frame.setVisible(false);
	}

	public class DbSessionRenderer implements ListCellRenderer<DbSession> {

		protected DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();

		@Override
		public Component getListCellRendererComponent(
				JList<? extends DbSession> list, DbSession value, int index,
				boolean isSelected, boolean cellHasFocus) {

			JLabel renderer = (JLabel) defaultRenderer
					.getListCellRendererComponent(list, value.getName(),
							index, isSelected, cellHasFocus);

			return renderer;
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		try {
			if (e.getSource() == removeButton) {
				controller.remove(list.getSelectedValue());
			} else if (e.getSource() == closeButton) {
				controller.close();
			} else if (e.getSource() == openButton) {
				controller.open(list.getSelectedValue());
			} else if (e.getSource() == saveButton) {
				controller.save(nameTextArea.getText());
			}
		} catch (Exception exception) {
			controller.reportException(exception);
		}
	}

	public void modelChanged(RemoteSessionsModel model) {
		quotaBar.setMaximum(model.getMaxQuota());
		quotaBar.setValue(model.getDiskUsage());
		String diskUsage = Strings.toHumanReadable(model.getDiskUsage()).trim();
		String quota = Strings.toHumanReadable(model.getMaxQuota()).trim();
		quotaBar.setString("Disk usage: " + diskUsage + "B / " + quota + "B");
		if (model.getDiskUsage() < 10_000_000) {
			quotaBar.setForeground(LOW_DISK_USAGE_COLOR);
		} else {
			quotaBar.setForeground(HIGH_DISK_USAGE_COLOR);
		}	
		list.setListData(model.getSessions().toArray(new DbSession[0]));
	}

	public void setController(RemoteSessionsController controller) {
		this.controller = controller;
	}
}
