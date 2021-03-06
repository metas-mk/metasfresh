package de.metas.handlingunits.allocation.impl;

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


import java.math.BigDecimal;

import org.adempiere.ad.service.IDeveloperModeBL;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.util.Services;
import org.adempiere.util.lang.ObjectUtils;
import org.compiere.util.Util;

import de.metas.handlingunits.IHUCapacityDefinition;
import de.metas.handlingunits.allocation.IAllocationRequest;
import de.metas.handlingunits.allocation.IAllocationResult;
import de.metas.handlingunits.model.I_M_HU_Item;
import de.metas.handlingunits.model.X_M_HU_PI_Item;
import de.metas.handlingunits.storage.IHUItemStorage;

public class UpperBoundAllocationStrategy extends AbstractFIFOStrategy
{
	// services
	private final transient IDeveloperModeBL developerModeBL = Services.get(IDeveloperModeBL.class);

	private final IHUCapacityDefinition _capacityOverride;

	public UpperBoundAllocationStrategy(final IHUCapacityDefinition capacityOverride)
	{
		super(false); // outTrx=false

		_capacityOverride = isUseDefaultCapacity(capacityOverride) ? null : capacityOverride;
	}

	private static final boolean isUseDefaultCapacity(final IHUCapacityDefinition capacity)
	{
		if (capacity == null)
		{
			return true;
		}

		final BigDecimal qty = capacity.getCapacity();
		return Util.same(IHUCapacityDefinition.DEFAULT, qty);
	}

	@Override
	public String toString()
	{
		return ObjectUtils.toString(this);
	}

	@Override
	protected IHUItemStorage getHUItemStorage(final I_M_HU_Item item, final IAllocationRequest request)
	{
		final IHUItemStorage storage = super.getHUItemStorage(item, request);

		// make sure that the capacity is forced by the user, not the system
		// If capacityOverride is null it means that we were asked to take the defaults
		final IHUCapacityDefinition capacityOverride = getCapacityOverride(request);
		if (capacityOverride != null && !storage.isPureVirtual())
		{
			storage.setCustomCapacity(capacityOverride);
		}

		return storage;
	}

	private final IHUCapacityDefinition getCapacityOverride(final IAllocationRequest request)
	{
		return _capacityOverride;
	}

	@Override
	protected IAllocationResult allocateOnIncludedHUItem(final I_M_HU_Item item, final IAllocationRequest request)
	{
		// Prevent allocating on a included HU item
		final String itemType = handlingUnitsBL.getItemType(item);
		if (X_M_HU_PI_Item.ITEMTYPE_HandlingUnit.equals(itemType))
		{
			if (developerModeBL.isEnabled())
			{
				throw new AdempiereException("HUs which are used in " + this + " shall not have included HUs. They shall be pure TUs."
						+ "\n Item: " + item
						+ "\n PI: " + item.getM_HU().getM_HU_PI_Version().getM_HU_PI().getName()
						+ "\n Request: " + request);
			}
			return AllocationUtils.nullResult();
		}

		//
		// We are about to allocate to Virtual HUs linked to given "item" (which shall be of type Material).
		// In this case we relly on standard logic.
		return super.allocateOnIncludedHUItem(item, request);
	}

	@Override
	protected IAllocationResult allocateRemainingOnIncludedHUItem(final I_M_HU_Item item, final IAllocationRequest request)
	{
		return AllocationUtils.nullResult();
	}
}
