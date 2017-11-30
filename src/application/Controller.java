package application;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.LineUnavailableException;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import utilities.Utilities;

class errorMessage {
	Alert dialog;
	public errorMessage(String reason, String message) {
		dialog= new Alert(AlertType.ERROR);
		dialog.setTitle(reason);
		dialog.setContentText(message);
		dialog.showAndWait();
	}
	
}

public class Controller {
	
	@FXML
	private ImageView imageViewRow; // the image display window in the GUI
	@FXML
	private ImageView imageViewCol;
	@FXML
	private ImageView imageViewDiag;
	@FXML
	private ImageView imageViewRevDiag;
	@FXML
	private Slider slider; 
	@FXML
	private TextField heightInput,widthInput,sampleInput;
	@FXML
	private Button submit;
	double framesPlayed = 1;
	private VideoCapture capture;
	private ScheduledExecutorService timer; 
	
	
	Mat colSTI = new Mat();
	Mat rowSTI = new Mat();	
	Mat diagSTI = new Mat();
	Mat revDiagSTI = new Mat();
	
	private String getImageFilename() {
		// This method should return the filename of the image to be played
		// You should insert your code here to allow user to select the file
		FileChooser fileDialog = new FileChooser();
		fileDialog.setTitle("Select a Video File");
		File selectedFile = fileDialog.showOpenDialog(null);
		String fileName = selectedFile.getAbsolutePath();
		
		return fileName;
	}
	
	
	protected void createFrameGrabber() throws InterruptedException, LineUnavailableException { 
		 double framePerSecond =capture.get(Videoio.CAP_PROP_FPS);
		 double frameWidth = capture.get(Videoio.CAP_PROP_FRAME_WIDTH);
		 double frameHeight = capture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
		 
		 colSTI = new Mat(new Size(frameHeight,0),16);
		 rowSTI = new Mat(new Size(frameWidth,0),16);
		 diagSTI = new Mat(new Size(min(frameWidth, frameHeight), 0),16);
		 
		 int bins = (int) (1 + Math.log(frameHeight)/Math.log(2));;
		 if (capture != null && capture.isOpened()) { 
			 // the video must be open     
			
			 Runnable frameGrabber = new Runnable() {       
				 @Override      
				 public void run() { 
					 Mat frame = new Mat();
					 
					 if (capture.read(frame)) { 
						 // decode successfully
						int frameIndex = (int)framesPlayed;
						buildSTI(frame,frameIndex);
						
						framesPlayed++;
						
                    } else { 
                    	timer.shutdown();
                    	
                    	ArrayList<double[][]> colHistogramTable = new ArrayList<double[][]>();
                    	ArrayList<double[][]> rowHistogramTable = new ArrayList<double[][]>();
                    	ArrayList<double[][]> diagHistogramTable = new ArrayList<double[][]>();
                    	ArrayList<double[][]> revDiagHistogramTable = new ArrayList<double[][]>();

                    	
                    	//transpose to match project description logic
                    	colSTI = colSTI.t();
                    	rowSTI = rowSTI.t();
                    	diagSTI = diagSTI.t();
                    	revDiagSTI = revDiagSTI.t();
                    	
                    	Image imageRow = Utilities.mat2Image(rowSTI);
						Image imageCol = Utilities.mat2Image(colSTI);
						Image imageDiag = Utilities.mat2Image(diagSTI);
						Image imageRevDiag = Utilities.mat2Image(revDiagSTI);
						imageViewCol.setImage(imageCol);
						imageViewRow.setImage(imageRow);
						imageViewDiag.setImage(imageDiag);
						imageViewRevDiag.setImage(imageRevDiag);
                    	
                    	//build histograms               	
                    	for(int i = 0; i < colSTI.width(); i++) {
							buildHistogram(colSTI.col(i), colHistogramTable);
						}
                    	
                    	for(int i = 0; i < rowSTI.width(); i++) {
                    		buildHistogram(rowSTI.col(i), rowHistogramTable);
                    	}
                    	
                    	for(int i = 0; i < diagSTI.width(); i++) {
                     		buildHistogram(diagSTI.col(i), diagHistogramTable);
                      	}
                    	
                    	for(int i = 0; i < revDiagSTI.width(); i++) {
                     		buildHistogram(revDiagSTI.col(i), revDiagHistogramTable);
                      	}
                    	
                    	try {
							printHistograms(rowHistogramTable, "horizontal.csv");
	                    	printHistograms(colHistogramTable, "vertical.csv");
	                    	printHistograms(diagHistogramTable, "revDiagonal.csv");
	                    	printHistograms(revDiagHistogramTable, "diagonal.csv");
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						} catch (UnsupportedEncodingException e) {
							e.printStackTrace();
						}


                    	
                    	//analyze
                    	detectWipe(colHistogramTable, "Horizontal");
                    	detectWipe(rowHistogramTable, "Vertical");
                    	
                    	// Since the STIs are transposed, Diagonals are actually reverse diagonals
                    	detectWipe(revDiagHistogramTable, "Diagonal");
                    	detectWipe(diagHistogramTable, "Reverse Diagonal");
                    	
					 }
					 
				 }

				private void detectWipe(ArrayList<double[][]> table, String wipeDirection) {
					boolean detected = false;
					for(int i = 1; i < table.size(); i++) {
						double intersection = 0;
						for(int j = 0; j < bins; j++) {
							for(int k = 0; k < bins; k++) {
								intersection += min(table.get(i)[j][k], table.get(i-1)[j][k]);
							}
						}
						// Threshold value
						if(intersection < 0.5)  {
							detected = true;
							System.out.println(wipeDirection + " wipe detected between frames " + (i - 1) + " and " + i);
						}
					}
					
					if(!detected) {
						System.out.println("No " + wipeDirection + " wipe detected");
					}
					
				}

				private void printHistograms(ArrayList<double[][]> table, String fileName) throws FileNotFoundException, UnsupportedEncodingException {
					PrintWriter writer = new PrintWriter(fileName, "UTF-8");
					writer.println();
                	for(int i = 0; i < table.size(); i++) {
                		writer.println("Table" + i);
                		for(int j = 0; j < bins; j++) {
                			for(int k = 0; k < bins; k++) {
                				DecimalFormat df = new DecimalFormat ("#.#####");
                				String val = df.format(table.get(i)[j][k]);
                				writer.print(val + ",");
                			}
                			writer.println();
                		}
                	}
                	writer.close();
				}
				
				private void buildHistogram(Mat column, ArrayList<double[][]> table) {
	
					int[][] histogram = new int[bins][bins];
					for(int i = 0; i < column.height(); i++) {
						int[] rgb = getRGB(column.get(i, 0));
						double[] chromaticity = chromaticity(rgb);
						int red = (int)Math.ceil(chromaticity[0] * bins) - 1;
						int green = (int)Math.ceil(chromaticity[1] * bins) - 1;
						if(red == -1) red = 0;
						if(green == -1) green = 0;
						
						histogram[red][green]++;
					}
					
					//Normalize
					double[][] normalizedHistogram = new double[bins][bins];
					for(int i = 0; i < bins; i++) {
						for(int j = 0; j < bins; j++) {
							normalizedHistogram[i][j] = (double)histogram[i][j] / column.height();
						}
					}
					table.add(normalizedHistogram);
				}
				
			 }; //end of runnable
			 
			 if (timer != null && !timer.isShutdown()) {
			    timer.shutdown();
			    timer.awaitTermination(Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
			 }  
			    // run the frame grabber     
			  timer = Executors.newSingleThreadScheduledExecutor();  
			  timer.scheduleAtFixedRate(frameGrabber, 0,  1, TimeUnit.MILLISECONDS);
			    
		 }
	}
	
	public double min(double val1, double val2) {
		if(val1 < val2) return val1;
		return val2;
	}
	
	//Builds both the col and row STI matrices
	public void buildSTI(Mat frame,int index) {
		int middleRolIndex = Math.floorDiv(frame.height(), 2);
		int middleColIndex = Math.floorDiv(frame.width(), 2);
		Mat middle_col = frame.col(middleColIndex);
		Mat middle_row = frame.row(middleRolIndex);
		Mat diag = frame.diag(0);
		Mat revDiag = new Mat(1,frame.height(), 16);
		int p = 0;
		int q = frame.width() - 1;
		while(p != frame.height() && q >= 0) {
			revDiag.put(0,p,frame.get(p, q));
			p++;
			q--;
		}
			
		
		colSTI.push_back(middle_col.t());
		rowSTI.push_back(middle_row);
		diagSTI.push_back(diag.t());
		revDiagSTI.push_back(revDiag);
	}
	
	
	public int[] getRGB (double[] pixel) {	
		int[] RGB= new int[3]; // RGB order
		
		RGB[0]= (int)Math.floor(pixel[2]);
		RGB[1]=(int)Math.floor(pixel[1]);
		RGB[2]=(int)Math.floor(pixel[0]);
		return RGB;
	}
	
	// returns the chromaticty of {r,g} into a array [0] = r , [1] =g
	public double[] chromaticity(int[] pixel ) {
		double[] output = new double[2];
		double sum = pixel[0]+pixel[1]+pixel[2];
		
		if ( sum == 0) { 
			//case where its black, we just return 0 for both values
			output[0]=0;
			output[1]=0;
			return output;
		}
		double r =  pixel[0]/sum;
		double g = pixel[1]/sum;
		output[0] = r;
		output[1] = g;
		return output;
	}
	
	@FXML
	protected void openImage(ActionEvent event) throws InterruptedException {
			capture = new VideoCapture(getImageFilename());
			
			if (capture.isOpened()) {
				
				 Mat frame = new Mat();
				 if(capture.read(frame)) {
					 imageViewRow.setImage(Utilities.mat2Image(frame));
					 imageViewCol.setImage(Utilities.mat2Image(frame));
					 imageViewDiag.setImage(Utilities.mat2Image(frame));
					 imageViewRevDiag.setImage(Utilities.mat2Image(frame));
				 }			
			}
	}
	
	
	@FXML
	protected void playVideo(ActionEvent event) throws LineUnavailableException, InterruptedException {
		createFrameGrabber();	
	}

	
	
}
