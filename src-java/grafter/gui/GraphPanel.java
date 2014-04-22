package grafter.gui;

import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import javax.swing.JDesktopPane;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.border.SoftBevelBorder;
import javax.swing.border.BevelBorder;
import java.awt.Dimension;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Point;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.BoxLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.Box;
import javax.swing.AbstractListModel;
import javax.swing.ScrollPaneConstants;

public class GraphPanel extends JPanel {
	private JTable graphTable;

	/**
	 * Create the panel.
	 */
	public GraphPanel() {
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		
		JPanel toolbarPanel = new JPanel();
		FlowLayout fl_toolbarPanel = (FlowLayout) toolbarPanel.getLayout();
		fl_toolbarPanel.setAlignment(FlowLayout.LEFT);
		add(toolbarPanel);
		
		JToolBar toolBar = new JToolBar();
		toolBar.setFloatable(false);
		toolbarPanel.add(toolBar);
		toolBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		JButton btnNewButton = new JButton("Import Graph");
		toolBar.add(btnNewButton);
		
		JButton btnAddTriple = new JButton("Add Triple");
		toolBar.add(btnAddTriple);
		
		JPanel mainPanel = new JPanel();
		add(mainPanel);
		mainPanel.setLayout(new BorderLayout(0, 0));
		
		JSplitPane splitPane = new JSplitPane();
		mainPanel.add(splitPane);
		splitPane.setResizeWeight(0.15);
		splitPane.setContinuousLayout(true);
		splitPane.setOneTouchExpandable(true);
		splitPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		
		JTabbedPane graphTabs = new JTabbedPane(JTabbedPane.TOP);
		graphTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		graphTabs.setBorder(null);
		splitPane.setRightComponent(graphTabs);
		
		JScrollPane tableScrollPanel = new JScrollPane();
		tableScrollPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		tableScrollPanel.setViewportBorder(null);
		tableScrollPanel.setBorder(null);
		graphTabs.addTab("Table View", null, tableScrollPanel, null);
		
		graphTable = new JTable();
		graphTable.setBorder(null);
		graphTable.setFillsViewportHeight(true);
		graphTable.setCellSelectionEnabled(true);
		graphTable.setColumnSelectionAllowed(true);
		tableScrollPanel.setViewportView(graphTable);
		
		JScrollPane graphScrollPanel = new JScrollPane();
		graphScrollPanel.setBorder(null);
		graphTabs.addTab("Graph View", null, graphScrollPanel, null);
		
		JPanel graphPanel = new JPanel();
		graphScrollPanel.setViewportView(graphPanel);
		
		JPanel panel_2 = new JPanel();
		splitPane.setLeftComponent(panel_2);
		panel_2.setLayout(new BoxLayout(panel_2, BoxLayout.Y_AXIS));
		
		JScrollPane scrollPane = new JScrollPane();
		panel_2.add(scrollPane);
		
		JList list = new JList();
		list.setBorder(new EmptyBorder(0, 0, 5, 0));
		scrollPane.setViewportView(list);
		//splitPane.setFocusTraversalPolicy(new FocusTraversalOnArray(new Component[]{graphTabs}));

	}

}
