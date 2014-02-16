package de.dietzm.gcodesim;

import java.awt.GraphicsConfiguration;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

public class OptionFrame extends JFrame implements ActionListener{

	@Override
	public void actionPerformed(ActionEvent arg0) {
		// TODO Auto-generated method stub
		
	}


	public OptionFrame() throws HeadlessException {
		setTitle("Options");
		setSize(400, 600);
		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("General",getGeneral());
		tabs.addTab("Graphics", getGraphic());
		tabs.addTab("Communication", new JPanel());
		tabs.addTab("Printer", new JPanel());
		add(tabs);
	}
	
	
	public JPanel getGeneral(){
		JPanel pan =  new JPanel();
		JCheckBox debug = new JCheckBox("Debug logging");
		debug.setActionCommand("debug");
		debug.addActionListener(this);
		pan.add(debug );
		
		return pan;
	}
	/*
	 *  render theme
	 * antialissing ?
	 * paintone layer at a time
	 * paint non print moves
	 * paint extruder
	 * */
	public JPanel getGraphic(){
		JPanel pan =  new JPanel();
		JCheckBox debug = new JCheckBox("Paint non-print moves");
		debug.setActionCommand("debug");
		debug.addActionListener(this);
		pan.add(debug );
		JCheckBox b2 = new JCheckBox("Paint on layer at a time");
		b2.setActionCommand("debug");
		b2.addActionListener(this);
		pan.add(b2 );
		return pan;
	}
/**
 * gcode logo
 * debug logging
 * play tone
 * 
 *
 * 
 * 
 * baud rate
 * comm timeout
 * reset on connect
 * 
 * bedsize
 * tempwatch intervall
 * flip x/y
 * speed for manual
 * home when finish
 */
	

}
