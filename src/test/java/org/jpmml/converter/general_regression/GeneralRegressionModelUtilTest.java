/*
 * Copyright (c) 2017 Villu Ruusmann
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
package org.jpmml.converter.general_regression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.dmg.pmml.FieldName;
import org.dmg.pmml.general_regression.GeneralRegressionModel;
import org.dmg.pmml.general_regression.PCell;
import org.dmg.pmml.general_regression.PPCell;
import org.dmg.pmml.general_regression.ParameterCell;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ModelEncoder;
import org.jpmml.converter.SchemaUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GeneralRegressionModelUtilTest {

	@Test
	public void encodeRegressionTable(){
		ModelEncoder encoder = new ModelEncoder();

		GeneralRegressionModel generalRegressionModel = new GeneralRegressionModel();

		generalRegressionModel = GeneralRegressionModelUtil.encodeRegressionTable(generalRegressionModel, Collections.<Feature>emptyList(), null, Collections.<Double>emptyList(), null);

		assertState(generalRegressionModel, false, false, false);

		Feature feature = SchemaUtil.createConstantFeature(encoder, 3d);

		generalRegressionModel = GeneralRegressionModelUtil.encodeRegressionTable(generalRegressionModel, Collections.singletonList(feature), 1d, Collections.singletonList(2d), null);

		assertState(generalRegressionModel, true, true, false);

		assertParameter(generalRegressionModel, "p0", 1d, Collections.<FieldName>emptyList());
		assertParameter(generalRegressionModel, "p1", (2d * 3d), Collections.<FieldName>emptyList());

		generalRegressionModel = new GeneralRegressionModel();

		feature = SchemaUtil.createInteractionFeature(encoder, FieldName.create("x1"), 5d, FieldName.create("x2"));

		generalRegressionModel = GeneralRegressionModelUtil.encodeRegressionTable(generalRegressionModel, Collections.singletonList(feature), 1d, Collections.singletonList(2d), null);

		assertState(generalRegressionModel, true, true, true);

		assertParameter(generalRegressionModel, "p0", 1d, Collections.<FieldName>emptyList());
		assertParameter(generalRegressionModel, "p1", (2d * 5d), Arrays.asList(FieldName.create("x1"), FieldName.create("x2")));
	}

	static
	private void assertState(GeneralRegressionModel generalRegressionModel, boolean hasParameters, boolean hasPCells, boolean hasPPCells){
		assertEquals(hasParameters, (generalRegressionModel.getParameterList()).hasParameters());
		assertEquals(hasPCells, (generalRegressionModel.getParamMatrix()).hasPCells());
		assertEquals(hasPPCells, (generalRegressionModel.getPPMatrix()).hasPPCells());
	}

	static
	private void assertParameter(GeneralRegressionModel generalRegressionModel, String parameterName, double beta, List<FieldName> predictorNames){
		List<PCell> pCells = getParameterCells((generalRegressionModel.getParamMatrix()).getPCells(), parameterName);
		List<PPCell> ppCells = getParameterCells((generalRegressionModel.getPPMatrix()).getPPCells(), parameterName);

		assertEquals(1, pCells.size());
		assertEquals(predictorNames.size(), ppCells.size());

		assertEquals((Double)beta, (Double)(pCells.get(0)).getBeta());

		for(int i = 0; i < predictorNames.size(); i++){
			assertEquals(predictorNames.get(i), (ppCells.get(i)).getField());
		}
	}

	static
	private <C extends ParameterCell> List<C> getParameterCells(List<C> cells, String parameterName){
		List<C> result = new ArrayList<>();

		for(C cell : cells){

			if((cell.getParameterName()).equals(parameterName)){
				result.add(cell);
			}
		}

		return result;
	}
}