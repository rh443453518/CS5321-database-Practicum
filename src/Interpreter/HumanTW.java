package Interpreter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import Tuple.Tuple;

/**
 * @author benzhang tang
 * HumanTW class class offers an efficient way to write tuples to a file of tuples
 * Feature: Buffer writer is used and the file is read line by line. 
 * A tuple is a line of ints separated by commas  
 */
public class HumanTW implements TupleWriter{
	private File file;
	private BufferedWriter bw;
	
	// Initializer
	public HumanTW (File file) throws IOException {
		this.file=file;
		bw = new BufferedWriter(new FileWriter(file));
	}
	
	@Override
	public void WriteTuple(Tuple t) throws IOException {
		// TODO Auto-generated method stub
		try {
			String stringResult = String.join(",", t.getTuple());
	        bw.write(stringResult);
	        bw.newLine();
		} catch (IOException e) {

			e.printStackTrace();
		}
	}

	public void close() throws IOException {
			bw.flush();
			bw.close();
	}
	
}
