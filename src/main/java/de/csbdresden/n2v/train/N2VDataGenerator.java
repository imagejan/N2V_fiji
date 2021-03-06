package de.csbdresden.n2v.train;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.scijava.log.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class N2VDataGenerator {

	public static <T extends RealType<T>> List<RandomAccessibleInterval<T>> generateBatchesFromList(List<RandomAccessibleInterval<T>> data, Interval shape) {

		List<RandomAccessibleInterval<T>> res = new ArrayList<>();

		data.forEach(img -> res.addAll(generateBatches(img, shape)));

		Collections.shuffle(res);

		return res;

	}

	static <T extends RealType<T>> List<RandomAccessibleInterval<T>> generateBatches(RandomAccessibleInterval<T> img, Interval shape) {

		return extractBatches(img, shape);

	}

	static <T extends RealType<T> & NativeType<T>> void augment(List<RandomAccessibleInterval<T>> tiles) {
		if(tiles.get(0).dimension(0) == tiles.get(0).dimension(1)) {
			//share in XY
			augmentBatches(tiles);
		}
//		Collections.shuffle(patches);
		for (int i = 0; i < tiles.size(); i++) {
			RandomAccessibleInterval<T> patch = tiles.get(i);
			RandomAccessibleInterval<T> rai = Views.addDimension(patch, 0, 0);
			rai = Views.addDimension(rai, 0, 0);
//			tiles.set(i, N2VUtils.copy(Views.zeroMin(rai)));
			tiles.set(i, Views.zeroMin(rai));
		}
	}

	private static <T extends RealType<T>> List<RandomAccessibleInterval<T>> extractBatches(RandomAccessibleInterval<T> img, Interval shape) {
		if(img.numDimensions() == shape.numDimensions()) return extractBatchesNoSlicing(img, shape);
		List<RandomAccessibleInterval<T>> res = new ArrayList<>();
		for (int i = 0; i < img.dimension(shape.numDimensions()); i++) {
			IntervalView<T> img1 = Views.hyperSlice(img, shape.numDimensions(), i);
			if(img1.numDimensions() == shape.numDimensions()) {
				res.addAll(extractBatchesNoSlicing(img1, shape));
			} else {
				for (int j = 0; j < img1.dimension(shape.numDimensions()); j++) {
					IntervalView<T> img2 = Views.hyperSlice(img1, shape.numDimensions(), j);
					if(img2.numDimensions() == shape.numDimensions()) {
						res.addAll(extractBatchesNoSlicing(img2, shape));
					} else {
						for (int k = 0; k < img2.dimension(shape.numDimensions()); k++) {
							IntervalView<T> img3 = Views.hyperSlice(img2, shape.numDimensions(), k);
							res.addAll(extractBatchesNoSlicing(img3, shape));

						}
					}
				}
			}
		}
		return res;
	}

	private static <T extends RealType<T>> List<RandomAccessibleInterval<T>> extractBatchesNoSlicing(RandomAccessibleInterval<T> img, Interval shape) {
		List<RandomAccessibleInterval<T>> res = new ArrayList<>();
		if(shapeTooBig(img, shape)) {
			System.out.println("N2VDataGenerator::extractPatchesNoSlicing: 'shape' is too big");
			return res;
		}
		if(shape.numDimensions() == 2) extractBatches2D(img, shape, res);
		else if(shape.numDimensions() == 3) extractBatches3D(img, shape, res);
		return res;
	}

	private static <T extends RealType<T>> void extractBatches2D(RandomAccessibleInterval<T> img, Interval shape, List<RandomAccessibleInterval<T>> res) {
		for (int y = 0; y <= img.dimension(1) - shape.dimension(1); y+=shape.dimension(1)) {
			for (int x = 0; x <= img.dimension(0) - shape.dimension(0); x+=shape.dimension(0)) {
				long[] min = {x, y};
				long[] max = {x + shape.dimension(0)-1, y + shape.dimension(1)-1};
//					System.out.println(res.size() + ": " + Arrays.toString(min) + " -> " + Arrays.toString(max));
				res.add(Views.interval(img,
						min,
						max));
			}
		}
	}

	private static <T extends RealType<T>> void extractBatches3D(RandomAccessibleInterval<T> img, Interval shape, List<RandomAccessibleInterval<T>> res) {
		for (int z = 0; z <= img.dimension(2) - shape.dimension(2); z+=shape.dimension(2)) {
			for (int y = 0; y <= img.dimension(1) - shape.dimension(1); y += shape.dimension(1)) {
				for (int x = 0; x <= img.dimension(0) - shape.dimension(0); x += shape.dimension(0)) {
					long[] min = {x, y, z};
					long[] max = {x + shape.dimension(0) - 1, y + shape.dimension(1) - 1, z + shape.dimension(2) - 1};
//					System.out.println(res.size() + ": " + Arrays.toString(min) + " -> " + Arrays.toString(max));
					res.add(Views.interval(img,
							min,
							max));
				}
			}
		}
	}

	private static <T extends RealType<T>> boolean shapeTooBig(RandomAccessibleInterval<T> img, Interval shape) {
		for (int i = 0; i < shape.numDimensions(); i++) {
			if(shape.dimension(i) > img.dimension(i)) return true;
		}
		return false;
	}

	static <T extends RealType<T>> void augmentBatches(List<RandomAccessibleInterval<T>> batches) {
		List<RandomAccessibleInterval<T>> augmented = new ArrayList<>();
		batches.forEach(patch -> {
			IntervalView<T> r1 = Views.zeroMin(Views.rotate(patch, 0, 1));
			IntervalView<T> r2 = Views.zeroMin(Views.rotate(r1, 0, 1));
			augmented.add(r1);
			augmented.add(r2);
			augmented.add(Views.zeroMin(Views.rotate(r2, 0, 1)));
		});
		batches.addAll(augmented);
		augmented.clear();
		batches.forEach(patch -> augmented.add(Views.zeroMin(Views.invertAxis(patch, 0))));
		batches.addAll(augmented);
	}

	static List< RandomAccessibleInterval<FloatType> > createTiles(RandomAccessibleInterval< FloatType > inputRAI, int trainDimensions, long batchDimLength, Logger logger ) {

		long maxBatchDimPossible = getSmallestInputDim(inputRAI, trainDimensions);

		maxBatchDimPossible = Math.min(batchDimLength, maxBatchDimPossible);
		if(maxBatchDimPossible < batchDimLength) {
			logger.warn("Cannot create batches of edge length " + batchDimLength + ", max possible length is " + maxBatchDimPossible);
		}
		long[] batchShapeData = new long[trainDimensions];
		Arrays.fill(batchShapeData, maxBatchDimPossible);
		FinalInterval batchShape = new FinalInterval(batchShapeData);
//		logger.info( "Creating tiles of size " + Arrays.toString(Intervals.dimensionsAsIntArray(batchShape)) + ".." );
		List< RandomAccessibleInterval< FloatType > > data = new ArrayList<>();
		data.add( inputRAI );
		List< RandomAccessibleInterval< FloatType > > tiles = N2VDataGenerator.generateBatchesFromList(
				data,
				batchShape);
		long[] tiledim = new long[ tiles.get( 0 ).numDimensions() ];
		tiles.get( 0 ).dimensions( tiledim );
		logger.info( "Generated " + tiles.size() + " tiles of shape " + Arrays.toString( tiledim ) );
//		RandomAccessibleInterval<FloatType> tilesStack = Views.stack(tiles);
//		uiService.show("tiles", tilesStack);
		return tiles;
	}

	private static long getSmallestInputDim(RandomAccessibleInterval<FloatType> img, int maxDimensions) {
		long res = img.dimension(0);
		for (int i = 1; i < img.numDimensions() && i < maxDimensions; i++) {
			if(img.dimension(i) < res) res = img.dimension(i);
		}
		return res;
	}
}
