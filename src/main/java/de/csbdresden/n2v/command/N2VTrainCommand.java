package de.csbdresden.n2v.command;

import de.csbdresden.n2v.N2VTraining;
import net.imagej.ImageJ;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.scijava.Cancelable;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.io.IOException;

@Plugin( type = Command.class, menuPath = "Plugins>CSBDeep>N2V>train" )
public class N2VTrainCommand implements Command, Cancelable {

	@Parameter(label = "Image used for training")
	private RandomAccessibleInterval< FloatType > training;

	@Parameter(label = "Image used for validation")
	private RandomAccessibleInterval< FloatType > validation;

	@Parameter(label = "Use 3D model instead of 2D")
	private boolean mode3D = false;

	@Parameter(required = false, visibility = ItemVisibility.MESSAGE)
	private String advancedLabel = "<html><br/><span style='font-weight: normal'>Advanced options</span></html>";

	@Parameter(label = "Number of epochs")
	private int numEpochs = 300;

	@Parameter(label = "Number of steps per epoch")
	private int numStepsPerEpoch = 200;

	@Parameter(label = "Batch size per step")
	private int batchSize = 180;

	@Parameter(label = "Dimension length of batch")
	private int batchDimLength = 180;

	@Parameter(label = "Dimension length of patch")
	private int patchDimLength = 60;

	@Parameter(label = "Neighborhood radius")
	private int neighborhoodRadius = 5;

	@Parameter(type = ItemIO.OUTPUT, label = "model from last training step")
	private String latestTrainedModelPath;

	@Parameter(type = ItemIO.OUTPUT, label = "model with lowest validation loss")
	private String bestTrainedModelPath;

	@Parameter
	private Context context;

	private boolean canceled;

	@Override
	public void run() {
		N2VTraining n2v = new N2VTraining(context);
		n2v.setTrainDimensions(mode3D ? 3 : 2);
		n2v.setNumEpochs(numEpochs);
		n2v.setStepsPerEpoch(numStepsPerEpoch);
		n2v.setBatchSize(batchSize);
		n2v.setBatchDimLength(batchDimLength);
		n2v.setPatchDimLength(patchDimLength);
		n2v.setNeighborhoodRadius(neighborhoodRadius);
		n2v.init();
		try {
			if(training.equals(validation)) {
				System.out.println("Using 10% of training data for validation");
				n2v.addTrainingAndValidationData(training, 0.1);
			} else {
				n2v.addTrainingData(training);
				n2v.addValidationData(validation);
			}
			n2v.train();
		}
		catch(Exception e) {
			n2v.dispose();
			e.printStackTrace();
			return;
		}
		try {
			File savedModel = n2v.exportLatestTrainedModel();
			if(savedModel == null) return;
			latestTrainedModelPath = savedModel.getAbsolutePath();
			savedModel = n2v.exportBestTrainedModel();
			bestTrainedModelPath = savedModel.getAbsolutePath();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean isCanceled() {
		return canceled;
	}

	@Override
	public void cancel(String reason) {
		canceled = true;
	}

	@Override
	public String getCancelReason() {
		return null;
	}

	public static void main( final String... args ) throws Exception {

		final ImageJ ij = new ImageJ();
		ij.launch( args );

		final File trainingImgFile = new File( "/home/random/Development/imagej/project/CSBDeep/train.tif" );

		if ( trainingImgFile.exists() ) {
			RandomAccessibleInterval _input = ( RandomAccessibleInterval ) ij.io().open( trainingImgFile.getAbsolutePath() );
			RandomAccessibleInterval _inputConverted = ij.op().convert().float32( Views.iterable( _input ) );
//			_inputConverted = Views.interval(_inputConverted, new FinalInterval(1024, 1024  ));

			RandomAccessibleInterval training = ij.op().copy().rai( _inputConverted );
			RandomAccessibleInterval prediction = training;

			ij.command().run( N2VTrainCommand.class, true,"training", training, "validation", prediction).get();
		} else
			System.out.println( "Cannot find training image " + trainingImgFile.getAbsolutePath() );

	}
}
