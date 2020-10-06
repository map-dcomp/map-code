package com.bbn.map.dcop;

import java.util.Objects;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.map.AbstractPeriodicService;
import com.bbn.map.AgentConfiguration;
import com.bbn.map.AgentConfiguration.DcopAlgorithm;
import com.bbn.map.common.ApplicationManagerApi;
import com.bbn.map.dcop.acdiff.ACdiffAlgorithm;
import com.bbn.map.dcop.cdiff.CdiffAlgorithm;
import com.bbn.map.dcop.defaults.DefaultAlgorithm;
import com.bbn.map.dcop.modular_acdiff.ModularACdiffAlgorithm;
import com.bbn.map.dcop.modular_rcdiff.ModularRCdiffAlgorithm;
import com.bbn.map.dcop.rcdiff.RCdiffAlgorithm;
import com.bbn.map.dcop.rdiff.RdiffAlgorithm;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionPlan;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * The main entry point for DCOP. The {@link Controller} will use this class to
 * interact with DCOP. The {@link Controller} will start this service as
 * appropriate for the node.
 * 
 */
/**
 * @author khoihd Modification by cwayllace to merge first DCOP algorithm (from
 *         data center to neighbors) with second version (data center to leaves)
 */
/**
 * @author khoihd
 *
 */
public class DCOPService extends AbstractPeriodicService {

    // Common variables
    private static final Logger LOGGER = LoggerFactory.getLogger(DCOPService.class);
    private final RegionIdentifier regionID;
    private final DcopAlgorithm algorithm;
    private final CdiffPlusAlgorithm cdiffPlusAlgorithm;
    
    /**
     * Number of rounds of AP to wait between checking for new messages.
     */
    public static final int AP_ROUNDS_TO_SLEEP_BETWEEN_MESSAGE_CHECKS = 5;
    
    
//>>>>>>> DCOP
    /**
     * Construct a DCOP service.
     * 
     * @param nodeName
     *            the name of the node that this service is running on (for
     *            logging)
     * @param region
     *            the region that this DCOP is running in
     * @param dcopInfoProvider
     *            how to access MAP network state
     * @param applicationManager
     *            source of information about applications, including
     *            specifications and profiles
     *
     */
    public DCOPService(@Nonnull final String nodeName,
            @Nonnull final RegionIdentifier region,
            @Nonnull final DcopInfoProvider dcopInfoProvider,
            @Nonnull final ApplicationManagerApi applicationManager) {
        super("DCOP-" + nodeName, AgentConfiguration.getInstance().getDcopRoundDuration());
        this.regionID = region;
        this.algorithm = AgentConfiguration.getInstance().getDcopAlgorithm();      

        this.dcopInfoProvider = Objects.requireNonNull(dcopInfoProvider);
        this.applicationManager = Objects.requireNonNull(applicationManager, "application manager");

        if (DcopAlgorithm.CDIFF_PLUS.equals(algorithm)) {
            cdiffPlusAlgorithm = new CdiffPlusAlgorithm(region, dcopInfoProvider, applicationManager);
        } else {
            cdiffPlusAlgorithm = null;
        }
    }

    private final DcopInfoProvider dcopInfoProvider;

    /**
     * Where to get application specifications and profiles from
     */
    private final ApplicationManagerApi applicationManager;

    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "Cannot guarantee that DCOP will compute a non-null plan. Appears to be a bug in FindBugs")
    @Override
    protected void execute() {
        try {
            final RegionPlan prevPlan = dcopInfoProvider.getDcopPlan();
            final RegionPlan plan = computePlan();
            if (null == plan) {
                LOGGER.warn("DCOP produced a null plan, ignoring");
            } else if (!plan.equals(prevPlan)) {
                LOGGER.info("Publishing DCOP plan: {}", plan);
                dcopInfoProvider.publishDcopPlan(plan);
            }
        } catch (final Throwable t) {
            LOGGER.error("Got error computing DCOP plan. Skipping this round and will try again next round", t);
        }
    }

    private RegionPlan computePlan() {
        LOGGER.info("Using algorithm {}", this.algorithm);  
        LOGGER.info("Asynchronous message drop {}", AgentConfiguration.getInstance().getDcopAcdiffSimulateMessageDrops());  
        LOGGER.info("Asynchronous message drop rate {}", AgentConfiguration.getInstance().getDcopAcdiffSimulateMessageDropRate());  
        LOGGER.info("Asynchronous timeout {}", AgentConfiguration.getInstance().getDcopAcdiffTimeOut());

        switch (this.algorithm) {
        case DISTRIBUTED_ROUTING_DIFFUSION:
            final AbstractDcopAlgorithm rdiff = new RdiffAlgorithm(regionID, dcopInfoProvider, applicationManager);
            return rdiff.run();
        case DISTRIBUTED_CONSTRAINT_DIFFUSION:
            final CdiffAlgorithm cdiff = new CdiffAlgorithm(regionID, dcopInfoProvider, applicationManager);
            return cdiff.run();
        case ASYNCHRONOUS_CDIFF:
            final ACdiffAlgorithm acdiff = new ACdiffAlgorithm(regionID, dcopInfoProvider, applicationManager);
            return acdiff.run();
        case RC_DIFF:
            final RCdiffAlgorithm rcdiff = new RCdiffAlgorithm(regionID, dcopInfoProvider, applicationManager);
            return rcdiff.run();
        case MODULAR_RCDIFF:
            final ModularRCdiffAlgorithm modularRcdiff = new ModularRCdiffAlgorithm(regionID, dcopInfoProvider, applicationManager);    
            return modularRcdiff.run();
        case MODULAR_ACDIFF:
            ModularACdiffAlgorithm modularAcdiff = new ModularACdiffAlgorithm(regionID, dcopInfoProvider, applicationManager);    
            return modularAcdiff.run();
        case DEFAULT_PLAN:
            DefaultAlgorithm defaultAlg = new DefaultAlgorithm(regionID, dcopInfoProvider, applicationManager);    
            return defaultAlg.run();
        case CDIFF_PLUS:
            LOGGER.info("Running DCOP CDIFF + with period {} seconds", this.getPeriod().getSeconds());
            return cdiffPlusAlgorithm.computePlan(this);
        default:
            throw new IllegalArgumentException("DCOP algorithm " + this.algorithm + " is unknown");
        }

    }

}
