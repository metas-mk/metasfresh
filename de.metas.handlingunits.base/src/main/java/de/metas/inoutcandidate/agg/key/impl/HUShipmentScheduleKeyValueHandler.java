package de.metas.inoutcandidate.agg.key.impl;

/*
 * #%L
 * de.metas.handlingunits.base
 * %%
 * Copyright (C) 2015 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.adempiere.util.Services;
import org.adempiere.util.agg.key.IAggregationKeyValueHandler;

import de.metas.handlingunits.IHUShipperTransportationBL;
import de.metas.handlingunits.model.I_M_HU;
import de.metas.handlingunits.shipmentschedule.api.IShipmentScheduleWithHU;
import de.metas.shipping.model.I_M_ShippingPackage;

/**
 * AggregationKey value handler for Invoice Candidates in Material Tracking
 *
 * @author al
 */
public class HUShipmentScheduleKeyValueHandler implements IAggregationKeyValueHandler<IShipmentScheduleWithHU>
{
	private static final String VERSION = "1";

	@Override
	public List<Object> getValues(final IShipmentScheduleWithHU schedWithHU)
	{
		final List<Object> values = new ArrayList<Object>();

		values.add(VERSION);

		final I_M_HU vhu = schedWithHU.getVHU();
		values.addAll(getValues(vhu));

		final I_M_HU tuHU = schedWithHU.getM_TU_HU();
		values.addAll(getValues(tuHU));

		final I_M_HU luHU = schedWithHU.getM_LU_HU();
		values.addAll(getValues(luHU));

		return values;
	}

	private final List<Object> getValues(final I_M_HU hu)
	{
		if (hu == null)
		{
			return Collections.emptyList();
		}

		//
		// Services
		final IHUShipperTransportationBL huShipperTransportationBL = Services.get(IHUShipperTransportationBL.class);

		final List<Object> values = new ArrayList<Object>();

		final List<I_M_ShippingPackage> shippingPackages = huShipperTransportationBL.getShippingPackagesForHU(hu);
		for (final I_M_ShippingPackage shippingPackage : shippingPackages)
		{
			final int packagePartnerId = shippingPackage.getC_BPartner_ID();
			values.add(packagePartnerId);

			final int packageLocationId = shippingPackage.getC_BPartner_Location_ID();
			values.add(packageLocationId);

			final int shipperTransportationId = shippingPackage.getM_ShipperTransportation_ID();
			values.add(shipperTransportationId);
		}

		return values;
	}
}
