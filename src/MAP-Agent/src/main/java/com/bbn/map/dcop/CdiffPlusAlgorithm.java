package com.bbn.map.dcop;

import java.time.Duration;

import javax.annotation.Nonnull;

import org.protelis.lang.ProtelisLoader;
import org.protelis.vm.CodePathFactory;
import org.protelis.vm.ExecutionEnvironment;
import org.protelis.vm.ProtelisProgram;
import org.protelis.vm.ProtelisVM;
import org.protelis.vm.impl.HashingCodePathFactory;
import org.protelis.vm.impl.SimpleExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AbstractService.Status;
import com.bbn.map.AgentConfiguration;
import com.bbn.map.AgentConfiguration.DcopAlgorithm;
import com.bbn.map.ap.ApLogger;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;
import com.bbn.protelis.networkresourcemanagement.ResourceReport.EstimationWindow;
import com.bbn.protelis.networkresourcemanagement.ResourceSummary;
import com.google.common.hash.Hashing;

/**
 * Implementation of algorithm for {@link DcopAlgorithm#CDIFF_PLUS}.
 * 
 * @author jschewe
 *
 */
/* package */ class CdiffPlusAlgorithm implements ApLogger {

    /** The Protelis VM to be executed by the device */
    private final ProtelisVM vm;

    private final ExecutionEnvironment environment;

    private final CdiffPlusNetworkManager networkManager;

    private final Logger apLogger;

    private static final Logger LOGGER = LoggerFactory.getLogger(CdiffPlusAlgorithm.class);

    private ResourceSummary resourceSummary;

    private final DcopInfoProvider dcopInfoProvider;

    /**
     * 
     * @return the environment used by the VM
     */
    public ExecutionEnvironment getEnvironment() {
        return environment;
    }

    /* package */ CdiffPlusAlgorithm(@Nonnull final RegionIdentifier region,
            @Nonnull final DcopInfoProvider dcopInfoProvider,
            @Nonnull final ApplicationManagerApi applicationManager) {

        // Finish making the new device and add it to our collection
        this.dcopInfoProvider = dcopInfoProvider;
        networkManager = new CdiffPlusNetworkManager(dcopInfoProvider, region);
        environment = new SimpleExecutionEnvironment();
        resourceSummary = dcopInfoProvider.getRegionSummary(EstimationWindow.LONG);

        this.apLogger = LoggerFactory.getLogger(this.getClass().getName() + ".ap_program");

        final boolean isProgramAnoyomous = false;
        final String programPath = "/protelis/com/bbn/map/dcop/cdiffplus.pt";
        final ProtelisProgram program;
        if (isProgramAnoyomous) {
            program = ProtelisLoader.parseAnonymousModule(programPath);
        } else {
            program = ProtelisLoader.parse(programPath);
        }

        final CodePathFactory codePathFactory = new HashingCodePathFactory(Hashing.murmur3_128());
        vm = new ProtelisVM(program, new CdiffPlusExecutionContext(region, applicationManager, this, environment,
                networkManager, codePathFactory));
    }

    /**
     * 
     * @return a new plan or null if one cannot be computed
     */
    public RegionPlan computePlan(final DCOPService service) {
        // snapshot the summary once per plan computations
        resourceSummary = dcopInfoProvider.getRegionSummary(EstimationWindow.LONG);

        // run as many rounds as possible before running out of duration, then
        // return
        final Duration apRoundDuration = AgentConfiguration.getInstance().getApRoundDuration();
        final long apCyclesInPeriod = service.getPeriod().toMillis() / apRoundDuration.toMillis() - 1;
        LOGGER.debug("number of cycles is " + apCyclesInPeriod);
        for (int i = 0; Status.RUNNING == service.getStatus() && i < apCyclesInPeriod; i++) {
            LOGGER.debug("Cycle {}", i);
            vm.runCycle();
            try {
                Thread.sleep(apRoundDuration.toMillis());
            } catch (final InterruptedException e) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Got interrupted, likely time to shutdown, top of while loop will confirm.");
                }
            }
        }

        if (vm.getCurrentValue() instanceof RegionPlan) {
            RegionPlan plan = (RegionPlan) vm.getCurrentValue();
            // boolean emptyPlan = plan.getPlan().isEmpty();
            return plan;
            // if (emptyPlan == false) {
            // return plan;
            // }
            // else
            // {
            // return null;
            // }
        } else {
            return null;
        }
    }

    /**
     * 
     * @return the summary as of the last call to
     *         {@link #computePlan(DCOPService)}
     */
    public ResourceSummary getResourceSummary() {
        return resourceSummary;
    }

    // ----- ApLogger
    @Override
    public void apErrorMessage(final String str) {
        apLogger.error(str);
    }

    @Override
    public void apWarnMessage(final String str) {
        apLogger.warn(str);
    }

    @Override
    public void apInfoMessage(final String str) {
        apLogger.info(str);
    }

    @Override
    public void apDebugMessage(final String str) {
        apLogger.debug(str);
    }

    @Override
    public void apTraceMessage(final String str) {
        apLogger.trace(str);
    }
    // ----- end ApLogger

}
