package de.dietzm.gcodesim;

import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

import de.dietzm.Constants;
import de.dietzm.Position;

public class OptionFrame extends JFrame implements ActionListener{

	@Override
	public void actionPerformed(ActionEvent arg0) {
		if(arg0.getActionCommand().equals("bedsize")){		
			JComboBox<String> sel=	(JComboBox<String>) arg0.getSource();
			String item = (String) sel.getSelectedItem();
			
			if(item.equals("150x150")){
				GcodeSimulator.bedsizeX=150;
				GcodeSimulator.bedsizeY=GcodeSimulator.bedsizeX;		
			}else if(item.equals("200x200")){
				GcodeSimulator.bedsizeX=200;
				GcodeSimulator.bedsizeY=GcodeSimulator.bedsizeX;
			}else if(item.equals("300x300")){
				GcodeSimulator.bedsizeX=300;
				GcodeSimulator.bedsizeY=GcodeSimulator.bedsizeX;
			}else if(item.equals("500x500")){
				GcodeSimulator.bedsizeX=500;
				GcodeSimulator.bedsizeY=GcodeSimulator.bedsizeX;
			}
			GcodeSimulator.storeConfig();
			JOptionPane.showMessageDialog(null, "Please restart GCodeSimulator now");
			setVisible(false);
			return;
		}
		if(arg0.getActionCommand().equals("theme")){		
			JComboBox<String> sel=	(JComboBox<String>) arg0.getSource();
			String item = (String) sel.getSelectedItem();
			GcodeSimulator.theme=item;
			GcodeSimulator.storeConfig();
			JOptionPane.showMessageDialog(null, "Please restart GCodeSimulator now");
			setVisible(false);
			return;
		}
		if(arg0.getActionCommand().equals("offset")){		
			JTextField txt=	(JTextField) arg0.getSource();
			String text = txt.getText();
			if(Constants.parseOffset(text)!= null){
				GcodeSimulator.dualoffsetXY=text;
				GcodeSimulator.storeConfig();
				JOptionPane.showMessageDialog(null, "Please restart GCodeSimulator now");
				setVisible(false);
			}else{
				JOptionPane.showMessageDialog(null, "Invalid Offset, Please enter x:y");
			}
			return;
		}
		
	}





	public OptionFrame() throws HeadlessException {
		setTitle("Options");
		setSize(400, 300);
		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("General",getGeneral());
//		tabs.addTab("Graphics", getGraphic());
//		tabs.addTab("Communication", new JPanel());
//		tabs.addTab("Printer", new JPanel());
		add(tabs);
	}
	
	
	public JPanel getGeneral(){
		JPanel pan =  new JPanel();
		pan.setLayout(new GridLayout(0,2));
		
		JLabel bz = new JLabel("Bedsize:");
		String[] bedsizes = {"150x150","200x200","300x300","500x500"};
		JComboBox<String> combo = new JComboBox<String>(bedsizes);
		combo.setActionCommand("bedsize");
	
		switch(GcodeSimulator.bedsizeX){
			case 150:
				combo.setSelectedIndex(0);
				break;
			case 200:
				combo.setSelectedIndex(1);
				break;
			case 300:
				combo.setSelectedIndex(2);
				break;
			case 500:
				combo.setSelectedIndex(3);
				break;
			default:
				combo.setSelectedIndex(0);
				break;
		}
		combo.addActionListener(this);
		pan.add(bz);
		pan.add(combo );
		
		JLabel th = new JLabel("Theme:");
		String[] themes = {"Default","Gray","Autumn"};
		JComboBox<String> thcom = new JComboBox<String>(themes);
		if(GcodeSimulator.theme.equalsIgnoreCase("gray")){
			thcom.setSelectedIndex(1);
		}else if(GcodeSimulator.theme.equalsIgnoreCase("autumn")){
			thcom.setSelectedIndex(2);
		}		
		thcom.setActionCommand("theme");
		thcom.addActionListener(this);
		
		
		pan.add(th);
		pan.add(thcom );
		
		JLabel dl = new JLabel("Debug logging:");
		JCheckBox debug = new JCheckBox();
		debug.setActionCommand("debug");
		debug.addActionListener(this);
		pan.add(dl );
		pan.add(debug );
		
		//extruder Offset
		JLabel oz = new JLabel("Dual Extruder offset (X:Y):");
		JTextField txt = new JTextField(GcodeSimulator.dualoffsetXY);
		txt.setActionCommand("offset");
		txt.addActionListener(this);
		pan.add(oz);
		pan.add(txt);
		
		
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
