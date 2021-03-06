/**
 *
 */
package de.metas.handlingunits.client.terminal.receipt.model;

/*
 * #%L
 * de.metas.handlingunits.client
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.adempiere.ad.table.api.IADTableDAO;
import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.ad.trx.api.ITrxManager;
import org.adempiere.bpartner.service.IBPartnerBL;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.service.ISysConfigBL;
import org.adempiere.uom.api.Quantity;
import org.adempiere.util.Check;
import org.adempiere.util.Services;
import org.adempiere.util.collections.Converter;
import org.adempiere.util.collections.Predicate;
import org.compiere.apps.ProcessCtl;
import org.compiere.model.I_AD_PInstance;
import org.compiere.model.I_AD_PInstance_Para;
import org.compiere.model.I_AD_Process;
import org.compiere.model.I_C_Order;
import org.compiere.model.I_C_OrderLine;
import org.compiere.model.MPInstance;
import org.compiere.process.ProcessInfo;
import org.compiere.util.Language;
import org.compiere.util.TrxRunnable;

import de.metas.adempiere.form.terminal.IKeyLayoutSelectionModel;
import de.metas.adempiere.form.terminal.ITerminalKey;
import de.metas.adempiere.form.terminal.TerminalException;
import de.metas.adempiere.form.terminal.TerminalKeyListenerAdapter;
import de.metas.adempiere.form.terminal.context.ITerminalContext;
import de.metas.adempiere.form.terminal.context.ITerminalContextReferences;
import de.metas.handlingunits.allocation.ILUTUProducerAllocationDestination;
import de.metas.handlingunits.client.terminal.editor.model.IHUKey;
import de.metas.handlingunits.client.terminal.editor.model.IHUKeyFactory;
import de.metas.handlingunits.client.terminal.editor.model.impl.HUEditorModel;
import de.metas.handlingunits.client.terminal.lutuconfig.model.LUTUConfigurationEditorModel;
import de.metas.handlingunits.client.terminal.receiptschedule.model.ReceiptScheduleTableRow;
import de.metas.handlingunits.client.terminal.select.api.IPOSFiltering;
import de.metas.handlingunits.client.terminal.select.api.IPOSTableRow;
import de.metas.handlingunits.client.terminal.select.api.impl.ReceiptScheduleFiltering;
import de.metas.handlingunits.client.terminal.select.model.AbstractHUSelectModel;
import de.metas.handlingunits.client.terminal.select.model.IHUEditorCallback;
import de.metas.handlingunits.client.terminal.select.model.WarehouseKey;
import de.metas.handlingunits.document.IHUDocumentLine;
import de.metas.handlingunits.exceptions.HUException;
import de.metas.handlingunits.impl.IDocumentLUTUConfigurationManager;
import de.metas.handlingunits.model.I_M_HU;
import de.metas.handlingunits.model.I_M_HU_LUTU_Configuration;
import de.metas.handlingunits.model.I_M_ReceiptSchedule;
import de.metas.handlingunits.model.I_M_Warehouse;
import de.metas.handlingunits.receiptschedule.impl.ReceiptScheduleHUDocumentLine;
import de.metas.handlingunits.receiptschedule.impl.ReceiptScheduleHUGenerator;

/**
 * Wareneingang (POS).
 *
 * @author cg
 * @author tsa
 */
public class ReceiptScheduleHUSelectModel extends AbstractHUSelectModel
{
	// Services
	private final transient ISysConfigBL sysConfigBL = Services.get(ISysConfigBL.class);

	private static final String PARA_C_Orderline_ID = " C_Orderline_ID";
	private static final String PROPERTY_JasperButtonEnabled = "JasperButtonEnabled";

	private static final String SYSCONFIG_ReceiptScheduleHUPOSJasperProcess = "ReceiptScheduleHUPOSJasperProcess";
	private static final String SYSCONFIG_QtyCUReadonlyAlwaysIfNotVirtualPI = "de.metas.handlingunits.client.terminal.receipt.model.ReceiptScheduleHUSelectModel.QtyCUReadonlyAlwaysIfNotVirtualPI";
	private static final boolean DEFAULT_QtyCUReadonlyAlwaysIfNotVirtualPI = false; // false, according to 08310

	private final PurchaseOrderKeyLayout purchaseOrderKeyLayout;

	public ReceiptScheduleHUSelectModel(final ITerminalContext terminalContext)
	{
		super(terminalContext);
		setDisplayPhotoShootButton(true);
		setDisplayCloseLinesButton(true);

		//
		//
		purchaseOrderKeyLayout = new PurchaseOrderKeyLayout(terminalContext);
		//
		// Configure purchaseOrderKeyLayout selectionModel
		{
			final IKeyLayoutSelectionModel purchaseOrderKeyLayoutModel = purchaseOrderKeyLayout.getKeyLayoutSelectionModel();
			purchaseOrderKeyLayoutModel.setAllowKeySelection(true);
			purchaseOrderKeyLayoutModel.setToggleableSelection(true);
		}

		purchaseOrderKeyLayout.addTerminalKeyListener(new TerminalKeyListenerAdapter()
		{
			@Override
			public void keyReturned(final ITerminalKey key)
			{
				final PurchaseOrderKey purchaseOrderKey = (PurchaseOrderKey)key;
				onPurchaseOrderKeyPressed(purchaseOrderKey);
			}
		});

		load();
	}

	@Override
	protected final ReceiptScheduleFiltering getService()
	{
		final IPOSFiltering service = super.getService();
		Check.assumeInstanceOf(service, ReceiptScheduleFiltering.class, "service");
		return (ReceiptScheduleFiltering)service;
	}

	public PurchaseOrderKeyLayout getPurchaseOrderKeyLayout()
	{
		return purchaseOrderKeyLayout;
	}

	private void onPurchaseOrderKeyPressed(final PurchaseOrderKey key)
	{
		refreshLines(false); // forceRefresh=false
	}

	/**
	 *
	 * @return C_Order_ID of currently pressed {@link PurchaseOrderKey}
	 */
	public int getC_Order_ID()
	{
		final PurchaseOrderKey purchaseOrderKey = (PurchaseOrderKey)purchaseOrderKeyLayout.getKeyLayoutSelectionModel().getSelectedKeyOrNull();
		if (purchaseOrderKey == null)
		{
			return -1;
		}
		return purchaseOrderKey.getC_Order_ID();
	}

	/**
	 * Loads purchase order keys from the given <code>line</code>.
	 */
	@Override
	protected void loadKeysFromLines(final List<IPOSTableRow> lines)
	{
		final IPOSFiltering service = getService();
		final List<I_C_Order> purchaseOrders = service.getOrders(lines);
		purchaseOrderKeyLayout.createAndSetKeysFromOrders(purchaseOrders);
	}

	@Override
	protected void onWarehouseKeyPressed(final WarehouseKey key)
	{
		final int warehouseId = key == null ? -1 : key.getM_Warehouse_ID();
		final ITerminalContext terminalCtx = getTerminalContext();

		// task FRESH-305 keep the selected warehouse id as property in the context
		terminalCtx.setContextValue(I_M_Warehouse.COLUMNNAME_M_Warehouse_ID, warehouseId);

		getVendorKeyLayout().getKeyLayoutSelectionModel().onKeySelected(null);
		purchaseOrderKeyLayout.getKeyLayoutSelectionModel().onKeySelected(null);

		setActionButtonsEnabled();
	}

	/**
	 * Predicate used to filter retrieved rows ( {@link IPOSTableRow} ) based on current pressed Keys
	 */
	private final Predicate<IPOSTableRow> rowsFilter = new Predicate<IPOSTableRow>()
	{
		@Override
		public boolean evaluate(final IPOSTableRow row)
		{
			if (row == null)
			{
				return false;
			}

			final int currentBPartnerId = getC_BPartner_ID();
			if (currentBPartnerId > 0 && row.getC_BPartner().getC_BPartner_ID() != currentBPartnerId)
			{
				return false;
			}

			final int currentOrderId = getC_Order_ID();
			if (currentOrderId > 0 && row.getC_Order().getC_Order_ID() != currentOrderId)
			{
				return false;
			}

			return true;
		}
	};

	@Override
	protected Predicate<IPOSTableRow> getRowsFilter()
	{
		return rowsFilter;
	}

	@Override
	protected HUEditorModel createHUEditorModel(final Collection<IPOSTableRow> rows, final IHUEditorCallback<HUEditorModel> editorCallback)
	{
		Check.assumeNotEmpty(rows, "rows not null");

		//
		// Case: user selected one row
		if (rows.size() == 1)
		{
			final IPOSTableRow row = rows.iterator().next();
			return createHUEditorModel_SingleRow(row, editorCallback);
		}
		//
		// Case: user selected more then one row
		else
		{
			return createHUEditorModel_MultiRow(rows, editorCallback);
		}
	}

	/**
	 * Creates HUs and shows the HU Editor to user for the case when user is selecting ONLY one receipt schedule.
	 *
	 * @param row
	 * @param editorCallback
	 * @return
	 */
	private final HUEditorModel createHUEditorModel_SingleRow(final IPOSTableRow row, final IHUEditorCallback<HUEditorModel> editorCallback)
	{
		Check.assumeNotNull(row, "row not null");

		final ReceiptScheduleFiltering service = getService();

		//
		// Create HU generator
		final ReceiptScheduleHUGenerator huGenerator = new ReceiptScheduleHUGenerator();
		huGenerator.setContext(getTerminalContext());
		final I_M_ReceiptSchedule schedule = service.getReferencedObject(row);
		huGenerator.addM_ReceiptSchedule(schedule);

		//
		// Get/Create and Edit LU/TU configuration
		final IDocumentLUTUConfigurationManager lutuConfigurationManager = huGenerator.getLUTUConfigurationManager();

		final I_M_HU_LUTU_Configuration lutuConfiguration = lutuConfigurationManager
				.createAndEdit(new Converter<I_M_HU_LUTU_Configuration, I_M_HU_LUTU_Configuration>()
				{
					@Override
					public I_M_HU_LUTU_Configuration convert(final I_M_HU_LUTU_Configuration lutuConfiguration)
					{
						final List<I_M_HU_LUTU_Configuration> altConfigurations = lutuConfigurationManager.getCurrentLUTUConfigurationAlternatives();

						//
						// Ask user to edit the configuration in another dialog
						try (final ITerminalContextReferences refs = getTerminalContext().newReferences())
						{
							final LUTUConfigurationEditorModel lutuConfigurationEditorModel = createLUTUConfigurationEditorModel(lutuConfiguration, altConfigurations);

							if (!editorCallback.editLUTUConfiguration(lutuConfigurationEditorModel))
							{
								return null;// User cancelled => do nothing
							}

							//
							// Update the LU/TU configuration on which we are working using what user picked
							lutuConfigurationEditorModel.save(lutuConfiguration); // FIXME: pick the config which was edited
						}
						catch (Exception e)
						{
							throw AdempiereException.wrapIfNeeded(e);
						}

						return lutuConfiguration;
					}
				});

		//
		// No configuration => user cancelled => don't open editor
		if (lutuConfiguration == null)
		{
			return null;
		}

		//
		// Calculate the target CUs that we want to allocate
		final ILUTUProducerAllocationDestination lutuProducer = huGenerator.getLUTUProducerAllocationDestination();
		final Quantity qtyCUsTotal = lutuProducer.calculateTotalQtyCU();
		if (qtyCUsTotal.isInfinite())
		{
			throw new TerminalException("LU/TU configuration is resulting to infinite quantity: " + lutuConfiguration);
		}
		huGenerator.setQtyToAllocateTarget(qtyCUsTotal);

		//
		// Generate the HUs
		final List<I_M_HU> hus = huGenerator.generate();

		//
		// Create & return the HUEditor
		final HUEditorModel huEditorModel = createHUEditorModel(hus, Collections.singleton(row));
		return huEditorModel;
	}

	/**
	 * Creates HUs and shows the HU Editor to user for the case when user is selecting more then one receipt schedule.
	 *
	 * @param rows
	 * @param editorCallback
	 * @return
	 * @task http://dewiki908/mediawiki/index.php/08270_Wareneingang_POS_multiple_lines_in_1_TU_%28107035315495%29
	 */
	private final HUEditorModel createHUEditorModel_MultiRow(final Collection<IPOSTableRow> rows, final IHUEditorCallback<HUEditorModel> editorCallback)
	{
		final ITerminalContext terminalContext = getTerminalContext();
		final ReceiptScheduleFiltering service = getService();

		final List<I_M_HU> hus = new ArrayList<>(); // this will hold the LUTU-Editor's result

		try (final ITerminalContextReferences refs = getTerminalContext().newReferences())
		{
			//
			// Create LU/TU configuration panel
			// NOTE: we will create one CU Key for each receipt schedule
			final LUTUConfigurationEditorModel lutuConfigurationEditingModel = new LUTUConfigurationEditorModel(terminalContext);
			lutuConfigurationEditingModel.setQtyCUReadonly(false);
			final List<ReceiptScheduleCUKey> cuKeys = new ArrayList<>();
			Integer bpartnerId = null;
			for (final IPOSTableRow row : rows)
			{
				final ReceiptScheduleTableRow receiptScheduleRow = service.getReceiptScheduleTableRow(row);

				// Make sure all receipt schedules are about same BPartner
				final int receiptScheduleBPartnerId = receiptScheduleRow.getC_BPartner_ID();
				if (bpartnerId != null && bpartnerId != receiptScheduleBPartnerId)
				{
					throw new TerminalException("@NotMatched@: @C_BPartner_ID@");
				}
				bpartnerId = receiptScheduleBPartnerId;

				// Create CUKey for receipt schedule
				final ReceiptScheduleCUKey cuKey = new ReceiptScheduleCUKey(terminalContext, receiptScheduleRow);
				cuKeys.add(cuKey);
			}
			lutuConfigurationEditingModel.setCUKeys(cuKeys);

			//
			// Ask the user to customize it
			final boolean edited = editorCallback.editLUTUConfiguration(lutuConfigurationEditingModel);
			if (!edited)
			{
				// User cancelled => do nothing
				return null;
			}

			//
			// Create one VHU for each CU Key were user entered some quantity
			for (final ReceiptScheduleCUKey cuKey : cuKeys)
			{
				final I_M_HU vhu = cuKey.createVHU();
				if (vhu != null)
				{
					hus.add(vhu);
				}
			}
		}
		catch (final Exception e)
		{
			throw AdempiereException.wrapIfNeeded(e);
		}

		//
		// Create & return the HUEditor
		final HUEditorModel huEditorModel = createHUEditorModel(hus, rows);
		return huEditorModel;
	}

	private final HUEditorModel createHUEditorModel(final List<I_M_HU> hus, final Collection<IPOSTableRow> rows)
	{
		if (hus.isEmpty())
		{
			throw new HUException("@NotCreated@ @M_HU_ID@");
		}

		final ReceiptScheduleFiltering service = getService();
		final ITerminalContext terminalContext = getTerminalContext();

		//
		// Get HUDocumentLine if any
		final IHUDocumentLine documentLine;
		final boolean attributesEditableOnlyIfVHU;
		if (rows.size() == 1)
		{
			final I_M_ReceiptSchedule singleReceiptSchedule = service.getReferencedObject(rows.iterator().next());
			documentLine = new ReceiptScheduleHUDocumentLine(singleReceiptSchedule);
			attributesEditableOnlyIfVHU = false; // default
		}
		else
		{
			documentLine = null;
			// Attributes shall be editable ONLY for VHUs (08270)
			attributesEditableOnlyIfVHU = true;
		}

		//
		// Create a Root HU Key from HUs that were created
		final IHUKeyFactory keyFactory = terminalContext.getService(IHUKeyFactory.class);
		final IHUKey rootHUKey = keyFactory.createRootKey();
		final List<IHUKey> huKeys = keyFactory.createKeys(hus, documentLine);
		rootHUKey.addChildren(huKeys);

		//
		// Create HU Editor Model
		final ReceiptScheduleHUEditorModel huEditorModel = new ReceiptScheduleHUEditorModel(terminalContext);
		huEditorModel.setRootHUKey(rootHUKey);
		huEditorModel.setOriginalTopLevelHUs(hus);
		huEditorModel.setAttributesEditableOnlyIfVHU(attributesEditableOnlyIfVHU);

		return huEditorModel;
	}

	public final void doJasperPrint()
	{
		final I_M_ReceiptSchedule selectedReceiptSchedule = getSelectedReceiptSchedule();
		final I_C_OrderLine orderLine = selectedReceiptSchedule.getC_OrderLine();

		//
		// service
		final ISysConfigBL sysConfigBL = Services.get(ISysConfigBL.class);

		//
		// Get Process from SysConfig
		final String defaultValue = null;
		final String reportConfigValue = sysConfigBL.getValue(SYSCONFIG_ReceiptScheduleHUPOSJasperProcess,
				defaultValue,
				selectedReceiptSchedule.getAD_Client_ID(),
				selectedReceiptSchedule.getAD_Org_ID());
		Check.assumeNotNull(reportConfigValue, "Report SysConfig value not null for {}", SYSCONFIG_ReceiptScheduleHUPOSJasperProcess);

		final int reportProcessId = Integer.parseInt(reportConfigValue);
		final I_AD_Process reportProcess = InterfaceWrapperHelper.create(getCtx(), reportProcessId, I_AD_Process.class, ITrx.TRXNAME_None);

		//
		// Print report
		doJasperPrint0(reportProcess, orderLine);
	}

	/**
	 * Read-only logic for the jasper button.
	 *
	 * @return
	 */
	public boolean isJasperButtonEnabled()
	{
		// We only allow the button to be active if we have only one row selected.
		return getRowsSelected().size() == 1;
	}

	private void doJasperPrint0(final I_AD_Process process, final I_C_OrderLine orderLine)
	{
		final ITrxManager trxManagerService = Services.get(ITrxManager.class);

		Check.assumeNotNull(orderLine, "orderLine not null");
		final int orderLineId = orderLine.getC_OrderLine_ID();
		final I_C_Order order = orderLine.getC_Order();
		final Language bpartnerLaguage = Services.get(IBPartnerBL.class).getLanguage(getCtx(), order.getC_BPartner_ID());

		//
		// Create AD_PInstance
		final I_AD_PInstance pinstance = new MPInstance(getCtx(), process.getAD_Process_ID(), 0, 0);
		final ProcessInfo pi = new ProcessInfo(process.getName(), process.getAD_Process_ID());
		pi.setReportLanguage(bpartnerLaguage);

		// 07055: we need to commit the process parameters before calling the reporting process, because that process might in the end call the adempiereJasper server which won't have access to this
		// transaction.
		trxManagerService.run(new TrxRunnable()
		{
			@Override
			public void run(final String localTrxName) throws Exception
			{
				pinstance.setAD_Table_ID(Services.get(IADTableDAO.class).retrieveTableId(I_C_OrderLine.Table_Name));
				pinstance.setRecord_ID(orderLineId);
				InterfaceWrapperHelper.save(pinstance);

				//
				// Parameter: M_HU_ID
				final I_AD_PInstance_Para para_M_HU_ID = InterfaceWrapperHelper.newInstance(I_AD_PInstance_Para.class, pinstance);
				para_M_HU_ID.setAD_PInstance_ID(pinstance.getAD_PInstance_ID()); // have to manually set this
				para_M_HU_ID.setSeqNo(10);
				para_M_HU_ID.setParameterName(PARA_C_Orderline_ID);
				para_M_HU_ID.setP_Number(BigDecimal.valueOf(orderLineId));
				InterfaceWrapperHelper.save(para_M_HU_ID);

				//
				// ProcessInfo
				pi.setTableName(org.compiere.model.I_C_OrderLine.Table_Name);
				pi.setRecord_ID(orderLineId);
				pi.setTitle(process.getName());
				pi.setAD_PInstance_ID(pinstance.getAD_PInstance_ID());
			}
		});

		final ITerminalContext terminalContext = getTerminalContext();

		//
		// Execute report in a new transaction
		trxManagerService.run(new TrxRunnable()
		{
			@Override
			public void run(final String localTrxName) throws Exception
			{
				final ITrx localTrx = trxManagerService.get(localTrxName, false); // createNew=false
				ProcessCtl.process(
						null,      // ASyncProcess parent
						terminalContext.getWindowNo(),
						null,      // IProcessParameter
						pi,
						localTrx);
			}
		});
	}

	@Override
	public void setActionButtonsEnabled()
	{
		final boolean enabledNew = isJasperButtonEnabled();
		firePropertyChange(PROPERTY_JasperButtonEnabled, !enabledNew, enabledNew);
	}

	/**
	 * Gets selected {@link I_M_ReceiptSchedule}. If there is no selected receipt schedule, exception is thrown.
	 *
	 * @return selected receipt schedule; never return null
	 * @throws TerminalException if there is no selected receipt schedule
	 */
	public I_M_ReceiptSchedule getSelectedReceiptSchedule()
	{
		return getSelectedObject(I_M_ReceiptSchedule.class);
	}

	private final LUTUConfigurationEditorModel createLUTUConfigurationEditorModel(final I_M_HU_LUTU_Configuration lutuConfiguration, final List<I_M_HU_LUTU_Configuration> altConfigurations)
	{
		Check.assumeNotNull(lutuConfiguration, "lutuConfiguration not null");

		final LUTUConfigurationEditorModel lutuConfigurationEditingModel = new LUTUConfigurationEditorModel(getTerminalContext());

		final boolean isQtyCUReadonlyAlwaysIfNotVirtualPI = sysConfigBL.getBooleanValue(
				SYSCONFIG_QtyCUReadonlyAlwaysIfNotVirtualPI,
				DEFAULT_QtyCUReadonlyAlwaysIfNotVirtualPI); // default fallback if not configure
		if (isQtyCUReadonlyAlwaysIfNotVirtualPI)
		{
			lutuConfigurationEditingModel.setQtyCUReadonlyAlwaysIfNotVirtualPI(); // 07501, 08310: Qty CU shall be configurable R-O in Receipt Schedule POS
		}
		lutuConfigurationEditingModel.load(lutuConfiguration, altConfigurations);

		return lutuConfigurationEditingModel;
	}

	@Override
	public String toString()
	{
		return "ReceiptScheduleHUSelectModel [purchaseOrderKeyLayout=" + purchaseOrderKeyLayout + ", rowsFilter=" + rowsFilter + "]";
	}
}
