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
package org.jpmml.converter.regression;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Iterables;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.regression.CategoricalPredictor;
import org.dmg.pmml.regression.NumericPredictor;
import org.dmg.pmml.regression.PredictorTerm;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.regression.RegressionTable;
import org.jpmml.converter.BinaryFeature;
import org.jpmml.converter.BooleanFeature;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.ConstantFeature;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.ContinuousLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.InteractionFeature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PowerFeature;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;

public class RegressionModelUtil {

	private RegressionModelUtil(){
	}

	static
	public RegressionModel createRegression(List<? extends Feature> features, List<Double> coefficients, Double intercept, Schema schema){
		ContinuousLabel continuousLabel = (ContinuousLabel)schema.getLabel();

		RegressionModel regressionModel = new RegressionModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(continuousLabel), null)
			.addRegressionTables(createRegressionTable(features, coefficients, intercept));

		return regressionModel;
	}

	static
	public RegressionModel createBinaryLogisticClassification(List<? extends Feature> features, List<Double> coefficients, Double intercept, RegressionModel.NormalizationMethod normalizationMethod, boolean hasProbabilityDistribution, Schema schema){
		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();

		if(categoricalLabel.size() != 2){
			throw new IllegalArgumentException();
		}

		RegressionTable activeRegressionTable = RegressionModelUtil.createRegressionTable(features, coefficients, intercept)
			.setTargetCategory(categoricalLabel.getValue(1));

		RegressionTable passiveRegressionTable = RegressionModelUtil.createRegressionTable(Collections.<Feature>emptyList(), Collections.<Double>emptyList(), null)
			.setTargetCategory(categoricalLabel.getValue(0));

		RegressionModel regressionModel = new RegressionModel(MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(categoricalLabel), null)
			.setNormalizationMethod(normalizationMethod)
			.addRegressionTables(activeRegressionTable, passiveRegressionTable)
			.setOutput(hasProbabilityDistribution ? ModelUtil.createProbabilityOutput(categoricalLabel) : null);

		return regressionModel;
	}

	static
	public RegressionTable createRegressionTable(List<? extends Feature> features, List<Double> coefficients, Double intercept){

		if(features.size() != coefficients.size()){
			throw new IllegalArgumentException();
		}

		RegressionTable regressionTable = new RegressionTable(intercept != null ? intercept : 0d);

		for(int i = 0; i < features.size(); i++){
			Feature feature = features.get(i);
			Double coefficient = coefficients.get(i);

			if(coefficient.isNaN() || ValueUtil.isZero(coefficient)){
				continue;
			} // End if

			if(feature instanceof BinaryFeature){
				BinaryFeature binaryFeature = (BinaryFeature)feature;

				CategoricalPredictor categoricalPredictor = new CategoricalPredictor()
					.setName(binaryFeature.getName())
					.setValue(binaryFeature.getValue())
					.setCoefficient(coefficient);

				regressionTable.addCategoricalPredictors(categoricalPredictor);
			} else

			if(feature instanceof BooleanFeature){
				BooleanFeature booleanFeature = (BooleanFeature)feature;

				CategoricalPredictor categoricalPredictor = new CategoricalPredictor()
					.setName(booleanFeature.getName())
					.setValue("true")
					.setCoefficient(coefficient);

				regressionTable.addCategoricalPredictors(categoricalPredictor);
			} else

			if(feature instanceof ConstantFeature){
				ConstantFeature constantFeature = (ConstantFeature)feature;

				double value = (constantFeature.getValue()).doubleValue() * coefficient;

				regressionTable.setIntercept(regressionTable.getIntercept() + value);
			} else

			if(feature instanceof InteractionFeature){
				InteractionFeature interactionFeature = (InteractionFeature)feature;

				PredictorTerm predictorTerm = new PredictorTerm()
					.setName(interactionFeature.getName())
					.setCoefficient(coefficient);

				List<? extends Feature> inputFeatures = interactionFeature.getInputFeatures();
				for(Feature inputFeature : inputFeatures){

					if(inputFeature instanceof ConstantFeature){
						ConstantFeature constantFeature = (ConstantFeature)inputFeature;

						double value = (constantFeature.getValue()).doubleValue();

						predictorTerm.setCoefficient(predictorTerm.getCoefficient() * value);
					} else

					{
						inputFeature = inputFeature.toContinuousFeature();

						predictorTerm.addFieldRefs(inputFeature.ref());
					}
				}

				List<FieldRef> fieldRefs = predictorTerm.getFieldRefs();
				if(fieldRefs.size() == 0){
					regressionTable.setIntercept(regressionTable.getIntercept() + predictorTerm.getCoefficient());
				} else

				if(fieldRefs.size() == 1){
					FieldRef fieldRef = Iterables.getOnlyElement(fieldRefs);

					NumericPredictor numericPredictor = new NumericPredictor()
						.setName(fieldRef.getField())
						.setCoefficient(predictorTerm.getCoefficient());

					regressionTable.addNumericPredictors(numericPredictor);
				} else

				{
					regressionTable.addPredictorTerms(predictorTerm);
				}
			} else

			if(feature instanceof PowerFeature){
				PowerFeature powerFeature = (PowerFeature)feature;

				NumericPredictor numericPredictor = new NumericPredictor()
					.setName(powerFeature.getName())
					.setExponent(powerFeature.getPower())
					.setCoefficient(coefficient);

				regressionTable.addNumericPredictors(numericPredictor);
			} else

			{
				ContinuousFeature continuousFeature = feature.toContinuousFeature();

				NumericPredictor numericPredictor = new NumericPredictor()
					.setName(continuousFeature.getName())
					.setCoefficient(coefficient);

				regressionTable.addNumericPredictors(numericPredictor);
			}
		}

		return regressionTable;
	}
}