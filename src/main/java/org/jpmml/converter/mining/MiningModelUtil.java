/*
 * Copyright (c) 2016 Villu Ruusmann
 *
 * This file is part of JPMML-Converter
 *
 * JPMML-Converter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-Converter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-Converter.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.converter.mining;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.Output;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.True;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segment;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.regression.RegressionTable;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.ContinuousLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.regression.RegressionModelUtil;

public class MiningModelUtil {

	private MiningModelUtil(){
	}

	static
	public MiningModel createRegression(Schema schema, Model model){
		ContinuousLabel continuousLabel = (ContinuousLabel)schema.getLabel();

		Feature feature = MiningModelUtil.MODEL_PREDICTION.apply(model);

		RegressionTable regressionTable = RegressionModelUtil.createRegressionTable(Collections.singletonList(feature), null, Collections.singletonList(1d));

		RegressionModel regressionModel = new RegressionModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(continuousLabel), null)
			.addRegressionTables(regressionTable);

		return createModelChain(schema, Arrays.asList(model, regressionModel));
	}

	static
	public MiningModel createBinaryLogisticClassification(Schema schema, Model model, double coefficient, boolean hasProbabilityDistribution){
		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();

		if(categoricalLabel.size() != 2){
			throw new IllegalArgumentException();
		}

		Feature feature = MiningModelUtil.MODEL_PREDICTION.apply(model);

		RegressionTable activeRegressionTable = RegressionModelUtil.createRegressionTable(Collections.singletonList(feature), null, Collections.singletonList(coefficient))
			.setTargetCategory(categoricalLabel.getValue(0));

		RegressionTable passiveRegressionTable = RegressionModelUtil.createRegressionTable(Collections.<Feature>emptyList(), null, Collections.<Double>emptyList())
			.setTargetCategory(categoricalLabel.getValue(1));

		RegressionModel regressionModel = new RegressionModel(MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(categoricalLabel), null)
			.setNormalizationMethod(RegressionModel.NormalizationMethod.SOFTMAX)
			.addRegressionTables(activeRegressionTable, passiveRegressionTable)
			.setOutput(hasProbabilityDistribution ? ModelUtil.createProbabilityOutput(categoricalLabel) : null);

		return createModelChain(schema, Arrays.asList(model, regressionModel));
	}

	static
	public MiningModel createClassification(Schema schema, List<? extends Model> models, RegressionModel.NormalizationMethod normalizationMethod, boolean hasProbabilityDistribution){
		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();

		if(categoricalLabel.size() < 3 || categoricalLabel.size() != models.size()){
			throw new IllegalArgumentException();
		}

		List<RegressionTable> regressionTables = new ArrayList<>();

		for(int i = 0; i < categoricalLabel.size(); i++){
			Feature feature = MiningModelUtil.MODEL_PREDICTION.apply(models.get(i));

			RegressionTable regressionTable = RegressionModelUtil.createRegressionTable(Collections.singletonList(feature), null, Collections.singletonList(1d))
				.setTargetCategory(categoricalLabel.getValue(i));

			regressionTables.add(regressionTable);
		}

		RegressionModel regressionModel = new RegressionModel(MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(categoricalLabel), regressionTables)
			.setNormalizationMethod(normalizationMethod)
			.setOutput(hasProbabilityDistribution ? ModelUtil.createProbabilityOutput(categoricalLabel) : null);

		List<Model> segmentationModels = new ArrayList<>(models);
		segmentationModels.add(regressionModel);

		return createModelChain(schema, segmentationModels);
	}

	static
	public MiningModel createModelChain(Schema schema, List<? extends Model> models){
		Segmentation segmentation = createSegmentation(Segmentation.MultipleModelMethod.MODEL_CHAIN, models);

		Model lastModel = Iterables.getLast(models);

		MiningModel miningModel = new MiningModel(lastModel.getMiningFunction(), ModelUtil.createMiningSchema(schema))
			.setSegmentation(segmentation);

		return miningModel;
	}

	static
	public Segmentation createSegmentation(Segmentation.MultipleModelMethod multipleModelMethod, List<? extends Model> models){
		return createSegmentation(multipleModelMethod, models, null);
	}

	static
	public Segmentation createSegmentation(Segmentation.MultipleModelMethod multipleModelMethod, List<? extends Model> models, List<? extends Number> weights){

		if((weights != null) && (models.size() != weights.size())){
			throw new IllegalArgumentException();
		}

		List<Segment> segments = new ArrayList<>();

		for(int i = 0; i < models.size(); i++){
			Model model = models.get(i);
			Number weight = (weights != null ? weights.get(i) : null);

			Segment segment = new Segment()
				.setId(String.valueOf(i + 1))
				.setPredicate(new True())
				.setModel(model);

			if(weight != null && !ValueUtil.isOne(weight)){
				segment.setWeight(ValueUtil.asDouble(weight));
			}

			segments.add(segment);
		}

		Segmentation segmentation = new Segmentation(multipleModelMethod, segments);

		return segmentation;
	}

	private static final Function<Model, Feature> MODEL_PREDICTION = new Function<Model, Feature>(){

		@Override
		public Feature apply(Model model){
			Output output = model.getOutput();

			if(output == null || !output.hasOutputFields()){
				throw new IllegalArgumentException();
			}

			OutputField outputField = Iterables.getLast(output.getOutputFields());

			Feature feature = new ContinuousFeature(outputField.getName(), outputField.getDataType());

			return feature;
		}
	};
}