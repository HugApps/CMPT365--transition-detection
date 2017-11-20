package application;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

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
	
	private VideoCapture capture;
	private ScheduledExecutorService timer; 

	@FXML
	protected void submitParams(ActionEvent event) throws LineUnavailableException, InterruptedException {
		if(isPlaying == true) {
			return;
		}
		try {
			this.width = Integer.parseInt(widthInput.getText());
			this.height = Integer.parseInt(heightInput.getText());
			this.numberOfSamplesPerColumn = Integer.parseInt(sampleInput.getText());
			initFeqMap(this.width,this.height,this.numberOfSamplesPerColumn);
			
		}catch(Exception e) {
			System.out.println(e);
			new errorMessage("Invalid input","please enter a number");
		}
		
		
	}
	
	
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
		FileChooser fileDialog = new FileChooser();
		fileDialog.setTitle("Select a Video File");
		File selectedFile = fileDialog.showOpenDialog(null);
		String fileName = selectedFile.getAbsolutePath();
		
		return fileName;
	}
	
	
	protected void seekVideo() {
		double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
		double sliderVal = slider.getValue();
		sliderValue = sliderVal;
		capture.set(Videoio.CAP_PROP_POS_FRAMES, totalFrameCount * sliderVal);
	}
	
	protected void createFrameGrabber() throws InterruptedException, LineUnavailableException { 
		
		 if (capture != null && capture.isOpened()) { 
			 // the video must be open     
			 double framePerSecond =capture.get(Videoio.CAP_PROP_FPS);
			 Runnable frameGrabber = new Runnable() {       
				 @Override      
				 public void run() { 
					 isPlaying = true;
					 Mat frame = new Mat();
					 if (capture.read(frame)) { 
						 // decode successfully
						 
						 Image image = Utilities.mat2Image(frame);
						 imageView.setImage(Utilities.mat2Image(frame));
							if (image != null) {
								// convert the image from RGB to grayscale
								Mat grayImage = new Mat();
								Imgproc.cvtColor(frame, grayImage, Imgproc.COLOR_BGR2GRAY);
								Mat debugFrame = frame;
								// resize the image
								Mat resizedImage = new Mat();
								Imgproc.resize(grayImage, resizedImage, new Size(width, height));
								
								// quantization
								double[][] roundedImage = new double[resizedImage.rows()][resizedImage.cols()];
								for (int row = 0; row < resizedImage.rows(); row++) {
									for (int col = 0; col < resizedImage.cols(); col++) {
										roundedImage[row][col] = (double)Math.floor(resizedImage.get(row, col)[0]/numberOfQuantizionLevels) / numberOfQuantizionLevels;
									}
								}
								
								// I used an AudioFormat object and a SourceDataLine object to perform audio output. Feel free to try other options
						        AudioFormat audioFormat = new AudioFormat(sampleRate, sampleSizeInBits, numberOfChannels, true, true);
						        try {
					            SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
					            sourceDataLine.open(audioFormat, sampleRate);
					         
					            sourceDataLine.start();
					            
					            for (int col = 0; col < width; col++) {
					            	    byte[] audioBuffer = new byte[numberOfSamplesPerColumn];
					            	    
					            	for (int t = 1; t <= numberOfSamplesPerColumn; t++) {
					            		double signal = 0;
					                	for (int row = 0; row < height; row++) {
					                		int m = height - row - 1; // Be sure you understand why it is height rather width, and why we subtract 1 
					                		int time = t + col * numberOfSamplesPerColumn;
					                		double ss = Math.sin(2 * Math.PI * freq[m] * (double)time/sampleRate);
					                		signal += roundedImage[row][col] * ss;
					                	}
					                	double normalizedSignal = signal / height; // signal: [-height, height];  normalizedSignal: [-1, 1]
					                	audioBuffer[t-1] = (byte) (normalizedSignal*0x7F); // Be sure you understand what the weird number 0x7F is for
					            	}
					            	byte [] tmp =  new byte[fullAudioBuffer.length +audioBuffer.length];
					            	System.arraycopy(fullAudioBuffer,0,tmp,0,fullAudioBuffer.length);
					            	System.arraycopy(audioBuffer,0,tmp,fullAudioBuffer.length,audioBuffer.length);
					            	fullAudioBuffer = tmp.clone();
					            	
					            	sourceDataLine.write(audioBuffer, 0, numberOfSamplesPerColumn);
					            	
					            	
					            }
					            sourceDataLine.drain();
					            sourceDataLine.close();
						        } catch(Exception e ) {
						        	System.out.println(e.toString());
						        	new errorMessage("Exception",e.getMessage());
						        }
							} else {
								new errorMessage("Video done playing","see resource/output.wave for putputfile");
								isPlaying = false;
								
								return;
							}
						 double currentFrameNumber = capture.get(Videoio.CAP_PROP_POS_FRAMES);
						 double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
						 
						 if(slider.getValue() == sliderValue) {
							 slider.setValue(currentFrameNumber / totalFrameCount * (slider.getMax() - slider.getMin()));
							 sliderValue = slider.getValue();
						 } else {
							 seekVideo();
						 }
						 try {
						 Clip clip = AudioSystem.getClip();
						 clip.open(AudioSystem.getAudioInputStream(new File("resources/click.wav")));
						 clip.start();
						 
						 }catch(Exception exc) {
							 System.out.println(exc);
							 new errorMessage("Exception",exc.getMessage());
						 }
						 
                    } else { 
                    	// reach the end of the video
                         // create a runnable to fetch new frames periodically 
                    	 
                         capture.set(Videoio.CAP_PROP_POS_FRAMES, 0); 
                         writeToFile(fullAudioBuffer);
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
	
	
	private void writeToFile(byte[] buffer) {
	    InputStream audioStream = new ByteArrayInputStream(buffer);
	    AudioFormat audio = new AudioFormat(sampleRate, sampleSizeInBits, numberOfChannels, true, true);
	    AudioInputStream stream = new AudioInputStream(audioStream,audio,buffer.length);
        try {
			AudioSystem.write(stream,Type.WAVE,new File("resources/output.wav"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			new errorMessage("Write to file failed",e.getMessage());
		}
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
	
	@FXML
	protected void playVideo(ActionEvent event) throws LineUnavailableException, InterruptedException {
		if(freq== null || freq.length <=0) {
			new errorMessage("Missing Params","please set width ,height and samples per column before playing");
			return;
		}
		createFrameGrabber();
	}

}
