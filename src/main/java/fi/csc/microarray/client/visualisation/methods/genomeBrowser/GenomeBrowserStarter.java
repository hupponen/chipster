package fi.csc.microarray.client.visualisation.methods.genomeBrowser;

import java.awt.Cursor;
import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;


public class GenomeBrowserStarter {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		JFrame frame = new JFrame();
		GenomeBrowser plot = new GenomeBrowser();
		ChartPanel panel = new ChartPanel(new JFreeChart(plot));
		panel.setPreferredSize(new Dimension(800, 600));
		plot.chartPanel = panel;
		//SelectableChartPanel selPanel = new SelectableChartPanel(new JFreeChart(plot), plot);
		//selPanel.getChartPanel().addChartMouseListener(plot);
		
		//selPanel.getChartPanel().addMouseWheelListener(plot);
		
		panel.setCursor(new Cursor(Cursor.HAND_CURSOR));
		
		//panel.addMouseWheelListener(plot);
		
		for (View view : plot.getViews()){
			panel.addMouseListener(view);
			panel.addMouseMotionListener(view);
			panel.addMouseWheelListener(view);
		}
		
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		frame.add(panel);
		//frame.addMouseWheelListener(plot);
		frame.pack();
		frame.setVisible(true);
	}
}