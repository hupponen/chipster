package fi.csc.microarray.client.session;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import net.miginfocom.swing.MigLayout;

public class RemoteSessionsView implements ActionListener {
	
	JFrame frame = new JFrame();
	JPanel panel = new JPanel(new MigLayout("fill"));
	JButton removeButton = new JButton("Remove");
	JButton closeButton = new JButton("Close");
	JProgressBar quotaBar = new JProgressBar();
	JList list = new JList();
	private RemoteSessionsController controller;
	private RemoteSessionsModel model;
	
	public RemoteSessionsView(RemoteSessionsController controller, RemoteSessionsModel model) {
		
		model.setListener(this);
		this.controller = controller;
		
		removeButton.addActionListener(this);
		closeButton.addActionListener(this);
		
		panel.add(quotaBar, "growx, span, wrap");
		panel.add(list, "push, grow, span, wrap");
		panel.add(removeButton, "align right");
		panel.add(closeButton, "align right");
		
		frame.add(panel);
		frame.setSize(640, 480);
		frame.setVisible(true);
		
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == removeButton) {
			controller.remove(list.getSelectedValue().toString());
		} else if (e.getSource() == closeButton) {
			frame.setVisible(false);
		}
	}

	public void modelChanged() {
		quotaBar.setMaximum(model.getMaxQuota());
		quotaBar.setValue(model.getDiskUsage());
		list.setListData(model.getItems());		
	}
}
