package com.bbn.map.dcop;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.protelis.lang.datatype.DeviceUID;
import org.protelis.vm.CodePath;
import org.protelis.vm.NetworkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.google.common.collect.ImmutableMap;

/**
 * Network manager for use with {@link CdiffPlusExecutionContext}.
 * 
 * @author jschewe
 *
 */
/* package */ class CdiffPlusNetworkManager implements NetworkManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CdiffPlusNetworkManager.class);

    /**
     * 
     * @param dcopInfoProvider
     *            where to get dcop shared state from and send it to
     * @param region
     *            TODO
     */
    /* package */ CdiffPlusNetworkManager(@Nonnull final DcopInfoProvider dcopInfoProvider,
            @Nonnull final RegionIdentifier region) {
        this.dcopInfoProvider = Objects.requireNonNull(dcopInfoProvider);
        this.region = Objects.requireNonNull(region);
    }

    private final DcopInfoProvider dcopInfoProvider;
    private final RegionIdentifier region;

    @Override
    public Map<DeviceUID, Map<CodePath, Object>> getNeighborState() {
        final Map<DeviceUID, Map<CodePath, Object>> retval = new HashMap<>();

        final ImmutableMap<RegionIdentifier, DcopSharedInformation> allInfo = dcopInfoProvider
                .getAllDcopSharedInformation();
        LOGGER.trace("Received neighbor state: {}", allInfo);

        allInfo.forEach((region, shared) -> {
            // don't send nulls
            // don't send our information to ourselves
            if (null != shared.getProtelisState() && !region.equals(this.region)) {
                retval.put(region, shared.getProtelisState());
            }
        });

        LOGGER.trace("Returning state: {}", retval);

        return retval;
    }

    @Override
    public void shareState(final Map<CodePath, Object> toSend) {
        final DcopSharedInformation shared = new DcopSharedInformation();
        shared.setProtelisState(toSend);
        dcopInfoProvider.setLocalDcopSharedInformation(shared);
    }

}
