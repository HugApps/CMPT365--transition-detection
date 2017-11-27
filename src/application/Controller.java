package application;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.lang.model.type.ArrayType;
import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
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
	private ImageView imageView; // the image display window in the GUI
	@FXML
	private Slider slider; 
	@FXML
	private TextField heightInput,widthInput,sampleInput;
	@FXML
	private Button submit;
	private Mat image;

	private int width;
	private int height;
	private int sampleRate; // sampling frequency
	private int sampleSizeInBits;
	private int numberOfChannels;
	private double[] freq; // frequencies for each particular row
	private int numberOfQuantizionLevels;
	private int numberOfSamplesPerColumn;
	private double sliderValue; //Controller's knowledge of the value of the slider
	private Alert errorDialog ;
	private boolean isPlaying;
	private byte[] fullAudioBuffer;
	double framesPlayed = 1;
	private VideoCapture capture;
	private ScheduledExecutorService timer; 

	
	Mat colSTI = new Mat();
	Mat rowSTI = new Mat();
	int[][] histogram;
	
	ArrayList<int[][]> histogramTable = new ArrayList<int[][]>();
	@FXML
	private void initialize() {
		// Optional: You should modify the logic so that the user can change these values
		// You may also do some experiments with different values
		width = 64;
		height = 64;
		sampleRate = 8000;
		sampleSizeInBits = 8;
		numberOfChannels = 1;
		numberOfQuantizionLevels = 16;
		sliderValue = 0;
		isPlaying = false;
		numberOfSamplesPerColumn = 500;
	}
	
	private void initFeqMap(int width, int height , int samplesPerColumn) {
		fullAudioBuffer = new byte[samplesPerColumn];
		freq = new double[height]; // Be sure you understand why it is height rather than width
		freq[height/2-1] = 440.0; // 440KHz - Sound of A (La)
		for (int m = height/2; m < height; m++) {
			freq[m] = freq[m-1] * Math.pow(2, 1.0/12.0); 
		}
		for (int m = height/2-2; m >=0; m--) {
			freq[m] = freq[m+1] * Math.pow(2, -1.0/12.0); 
		}
		
	}
	private String getImageFilename() {
		// This method should return the filename of the image to be played
		// You should insert your code here to allow user to select the file
		/*FileChooser fileDialog = new FileChooser();
		fileDialog.setTitle("Select a Video File");
		File selectedFile = fileDialog.showOpenDialog(null);
		String fileName = selectedFile.getAbsolutePath();*/
		
		return "resources/test.mp4";
	}
	
	
	protected void createFrameGrabber() throws InterruptedException, LineUnavailableException { 
		 double framePerSecond =capture.get(Videoio.CAP_PROP_FPS);
		 double totalNumberFrames = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
		 double frameWidth = capture.get(Videoio.CAP_PROP_FRAME_WIDTH);
		 double frameHeight = capture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
		 
		 colSTI = new Mat(new Size(frameHeight,0),16);
		 rowSTI = new Mat(new Size(frameWidth,0),16);
		 
		 int ColBins = (int) (1 + Math.log(frameHeight)/Math.log(2));
		 histogram = new int[ColBins][ColBins];
		// System.out.println(ColBins);
		 if (capture != null && capture.isOpened()) { 
			 // the video must be open     
			
			 Runnable frameGrabber = new Runnable() {       
				 @Override      
				 public void run() { 
					 isPlaying = true;
					 Mat frame = new Mat();
					 
					 if (capture.read(frame)) { 
						 // decode successfully
						int frameIndex = (int)framesPlayed;
						buildSTI(frame,frameIndex);
						// compare histogram of chromaticty between different frames of middle col
						//buildHistogram(frame,histogram);
						Image image = Utilities.mat2Image(rowSTI);
						imageView.setImage(image);
						framesPlayed++;
						
                    } else { 
                    	// reach the end of the video
                         // create a runnable to fetch new frames periodically
                    	 isPlaying = false;
                         //capture.set(Videoio.CAP_PROP_POS_FRAMES, 0); 
                         return;
					 }
					 
				 }
			 };
			 if (timer != null && !timer.isShutdown()) {
			    timer.shutdown();
			    timer.awaitTermination(Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
			 }  
			    // run the frame grabber     
			  timer = Executors.newSingleThreadScheduledExecutor();  
			  timer.scheduleAtFixedRate(frameGrabber, 0, Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
			    
		 }
	}
	
	public void buildHistogram(Mat frame,int[][] histogram) {
		int middleColIndex = Math.floorDiv(frame.width(), 2);
		Mat middle_col = frame.col(middleColIndex);
		for (int row = 0 ; row <= middle_col.rows()-1;row++) {
			int[] currentPixel = getRGB(middle_col,0,row);//RGB
			int RIndex = currentPixel[0]%histogram[0].length;
			int GIndex = currentPixel[1]%histogram[1].length;
			int[] currentChromaticty = chromtacity(currentPixel);
			//histogram[RIndex][GIndex]=currentChromaticty;
			
		}
		
	}
	
	//Builds both the col and row STI matrices
	public void buildSTI(Mat frame,int index) {
		int middleRolIndex = Math.floorDiv(frame.height(), 2);
		int middleColIndex = Math.floorDiv(frame.width(), 2);
		Mat middle_col = frame.col(middleColIndex);
		Mat middle_row = frame.row(middleRolIndex);
		
		colSTI.push_back(middle_col.t());
		rowSTI.push_back(middle_row);
	}
	
	
	public int[] getRGB (Mat sti, int col,int row) {
		int[] RGB= new int[3]; // RGB order
		double[] BGR = sti.get(col,row);
		
		RGB[0]= (int)Math.floor(BGR[2]);
		RGB[1]=(int)Math.floor(BGR[1]);
		RGB[2]=(int)Math.floor(BGR[0]);
		return RGB;
	}
	
	// returns the chromaticty of {r,g} into a array [0] = r , [1] =g
	public int[] chromtacity(int[] pixel ) {
		int[] output = new int[2];
		int sum = pixel[0]+pixel[1]+pixel[2];
		if ( sum == 0) { 
			//case where its black, we just return 0 for both values ?
			output[0]=0;
			output[1]=0;
			return output;
		}
		int r =  pixel[0]/sum;
		int g = pixel[1]/sum;
		
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
					 Image im = Utilities.mat2Image(frame);
					 imageView.setImage(Utilities.mat2Image(frame));
				 }
				// we don't want to play the video as soon as it opens, for now we just grab the first frame of the video 			
			}
	}
	
	
	private String StiPrediction(Mat[] framesArray,int width,int height) {
		
		 int frameType = framesArray[0].type();
		 //rows x columns
		 Mat colSTI = new Mat(width,framesArray.length,frameType);
		 Mat rolSTI = new Mat(height,framesArray.length,frameType);
		 int middleRolIndex = Math.floorDiv(height, 2);
		 int middleColIndex = Math.floorDiv(width, 2);
		
		 for (int i = 0 ; i <=framesArray.length -1 ;i++) {
			 System.out.println(framesArray[i].size());
			 if(framesArray[i].size().height != 0 ||framesArray[i].size().width != 0) {
			   Mat middle_col = framesArray[i].col(middleColIndex);
			   Mat middle_row = framesArray[i].row(middleRolIndex);
			   middle_col = middle_col.t();
			   middle_row = middle_row.t();
			   middle_col.copyTo(colSTI.col(i));
			   middle_row.copyTo(rolSTI.col(i));
			 }
		 }
			 
		 Image im = Utilities.mat2Image(colSTI);
		 imageView.setImage(im);
		 return"done";
		
	}
	@FXML
	protected void playVideo(ActionEvent event) throws LineUnavailableException, InterruptedException {
		
		createFrameGrabber();
		
		/*double totalNumberFrames = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
		double frameWidth = capture.get(Videoio.CAP_PROP_FRAME_WIDTH);
		double frameHeight = capture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
		System.out.println(frameWidth);
		System.out.println(totalNumberFrames);
		Mat[] frameArray = new Mat[(int)totalNumberFrames];
		Mat frame = new Mat();
		int index = 0;
		Boolean hasFrame = true;
		while(true) {
			hasFrame =capture.read(frame);
			frameArray[index] = frame;
			
			if(hasFrame == false) {
				System.out.println("No frame");
				break;
			}
			
			if(index >= totalNumberFrames) {
				break;
			}
			System.out.println(index);
			index ++;
		}
		
		System.out.println(StiPrediction(frameArray,(int)frameWidth,(int)frameHeight));
		 //Define the STI matrix for horizontal*/
		
	}

	
	
}
