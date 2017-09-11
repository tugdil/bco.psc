package org.openbase.bco.psc.sm;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.openbase.bco.dal.remote.unit.AbstractUnitRemote;
import org.openbase.bco.dal.remote.unit.Units;
import org.openbase.bco.psc.lib.jp.JPLocalInput;
import org.openbase.bco.psc.lib.jp.JPLocalOutput;
import org.openbase.bco.psc.sm.jp.JPBaseScope;
import org.openbase.bco.psc.sm.jp.JPOutScope;
import org.openbase.bco.psc.sm.jp.JPRegistryIds;
import org.openbase.bco.psc.sm.jp.JPTransformFiles;
import org.openbase.bco.psc.sm.registry.RegistryTransformerFactory;
import org.openbase.bco.psc.sm.rsb.RSBConnection;
import org.openbase.bco.registry.remote.Registries;
import static org.openbase.bco.registry.remote.Registries.getUnitRegistry;
import org.openbase.jps.core.JPService;
import org.openbase.jps.exception.JPNotAvailableException;
import org.openbase.jps.exception.JPValidationException;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.exception.VerificationFailedException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.exception.printer.LogLevel;
import org.openbase.jul.storage.registry.RegistrySynchronizer;
import org.openbase.jul.storage.registry.SynchronizableRegistryImpl;
import org.slf4j.LoggerFactory;
import rsb.AbstractEventHandler;
import rsb.Event;
import rsb.MetaData;
import rsb.Scope;
import rst.domotic.unit.UnitConfigType;
import rst.tracking.TrackedPostures3DFloatType.TrackedPostures3DFloat;

/*
 * #%L
 * BCO PSC Skeleton Merging
 * %%
 * Copyright (C) 2016 - 2017 openbase.org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
/**
 *
 * @author <a href="mailto:thuppke@techfak.uni-bielefeld.de">Thoren Huppke</a>
 */
public class SkeletonMerging extends AbstractEventHandler {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SkeletonMerging.class);

    private RegistrySynchronizer<String, RegistryTransformer, UnitConfigType.UnitConfig, UnitConfigType.UnitConfig.Builder> selectableObjectRegistrySynchronizer;
    private SynchronizableRegistryImpl<String, RegistryTransformer> registryTransformerRegistry;
    private Map<Scope, String> scopeIdMap = new HashMap<>();
    private Map<Scope, FileTransformer> scopeFileTransformerMap = new HashMap<>();
    private boolean merging_required = false;

    private RSBConnection rsbConnection;

    @Override
    public void handleEvent(final Event event) {
        if (!(event.getData() instanceof TrackedPostures3DFloat) || rsbConnection.getOutScope().equals(event.getScope())) {
            return;
        }
        LOGGER.trace("New TrackedPostures3DFloat event received on scope " + event.getScope().toString());
        Optional<Scope> bestScope = event.getScope().superScopes(true).stream()
                .filter(s -> scopeIdMap.containsKey(s) || scopeFileTransformerMap.containsKey(s))
                .sorted((o1, o2) -> o2.toString().length() - o1.toString().length())
                .findFirst();
        if (!bestScope.isPresent()) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("No Transformer registered for the event's scope " + event.getScope().toString()),
                    LOGGER, LogLevel.TRACE);
            return;
        }
        try {
            Transformer currentTransformer;
            Scope scope = bestScope.get();
            if (scopeIdMap.containsKey(scope)) {
                LOGGER.trace("Using scope " + scope.toString() + " unit " + scopeIdMap.get(scope));
                currentTransformer = registryTransformerRegistry.get(scopeIdMap.get(scope));
            } else {
                LOGGER.trace("Using scope " + scope.toString() + " from file.");
                currentTransformer = scopeFileTransformerMap.get(scope);
            }

            TrackedPostures3DFloat postures = (TrackedPostures3DFloat) event.getData();
            TrackedPostures3DFloat transformedPostures = currentTransformer.transform(postures);

            if (merging_required) {
                //TODO merge the data here!
//                return;
            }

            LOGGER.trace("Creating transformed event.");
            Event transformedEvent = copyEventMetaData(event);
            transformedEvent.setData(transformedPostures);
            try {
                rsbConnection.sendTransformedEvent(transformedEvent);
            } catch (CouldNotPerformException ex) {
                ExceptionPrinter.printHistory(new CouldNotPerformException("Could not send the transformed postures.", ex), LOGGER, LogLevel.WARN);
            }
        } catch (CouldNotPerformException ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("Could not transform the postures.", ex), LOGGER, LogLevel.WARN);
        }
    }

    private Event copyEventMetaData(Event event) {
        Event copy = new Event(event.getData().getClass());
        MetaData meta = event.getMetaData();
        MetaData cMeta = copy.getMetaData();
        meta.userInfoKeys().forEach((key) -> {
            cMeta.setUserInfo(key, meta.getUserInfo(key));
        });
        meta.userTimeKeys().forEach((key) -> {
            cMeta.setUserTime(key, meta.getUserTime(key));
        });
        cMeta.setCreateTime(meta.getCreateTime());
        cMeta.setSendTime(meta.getSendTime());
        return copy;
    }

    public SkeletonMerging() {
        try {
            registryTransformerRegistry = new SynchronizableRegistryImpl<>();

            handleJPArguments();
            addShutdownHook();
            // Wait for events.
            while (true) {
                Thread.sleep(1);
            }
        } catch (Exception ex) {
            ExceptionPrinter.printHistory(new CouldNotPerformException("App failed", ex), LOGGER);
            System.exit(255);
        }
    }

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                rsbConnection.deactivate();
            } catch (CouldNotPerformException ex) {
                ExceptionPrinter.printHistory(ex, LOGGER);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                ExceptionPrinter.printHistory(ex, LOGGER);
            }
        }));
    }

    private void handleJPArguments() throws JPValidationException, JPNotAvailableException, CouldNotPerformException, InterruptedException {
        //TODO: remove this as soon as merging is enabled!
        if (JPService.getProperty(JPTransformFiles.class).isParsed() && JPService.getProperty(JPRegistryIds.class).isParsed()
                || JPService.getProperty(JPTransformFiles.class).isParsed() && JPService.getProperty(JPTransformFiles.class).getValue().size() > 1
                || JPService.getProperty(JPRegistryIds.class).isParsed() && JPService.getProperty(JPRegistryIds.class).getValue().size() > 1) {
            throw new JPValidationException("So far, only one transformer can be specified via -r or -f, as merging is not yet implemented.");
        }

        Scope baseScope = JPService.getProperty(JPBaseScope.class).getValue();
        Scope outScope = baseScope.concat(JPService.getProperty(JPOutScope.class).getValue());
        boolean registryRequired = false;
        if (!(JPService.getProperty(JPTransformFiles.class).isParsed() || JPService.getProperty(JPRegistryIds.class).isParsed())) {
            //TODO: Add config file or so?!
            throw new JPValidationException("At least one of --registry-id or --transform-file has to be specified");
        }

        if (JPService.getProperty(JPTransformFiles.class).isParsed()) {
            for (Entry<Scope, File> entry : JPService.getProperty(JPTransformFiles.class).getValue().entrySet()) {
                Scope scope = baseScope.concat(entry.getKey());
                scopeFileTransformerMap.put(scope, new FileTransformer(entry.getValue()));
                LOGGER.info("Registering on scope " + scope.toString() + " Transformer from file " + entry.getValue().getAbsolutePath());
            }
        }
        if (JPService.getProperty(JPRegistryIds.class).isParsed()) {
            registryRequired = true;
            for (Entry<Scope, String> entry : JPService.getProperty(JPRegistryIds.class).getValue().entrySet()) {
                Scope scope = baseScope.concat(entry.getKey());
                scopeIdMap.put(scope, entry.getValue());
                LOGGER.info("Registering on scope " + scope.toString() + " Unit with id " + entry.getValue());
            }
        }
        if (registryRequired) {
            if (scopeIdMap.keySet().stream().anyMatch(k -> scopeFileTransformerMap.containsKey(k))
                    || scopeFileTransformerMap.keySet().stream().anyMatch(k -> scopeIdMap.containsKey(k))) {
                throw new JPValidationException("The same scope appeared in the -f and the -r arguments, which is invalid.");
            }
            initializeRegistryConnection();
        }

        if (scopeIdMap.size() + scopeFileTransformerMap.size() > 1) {
            merging_required = true;
        }

        rsbConnection = new RSBConnection(this, baseScope, outScope);
    }

    private void initializeRegistryConnection() throws InterruptedException, CouldNotPerformException {
        try {
            LOGGER.info("Initializing Registry synchronization.");
            Registries.getUnitRegistry().waitForData(3, TimeUnit.SECONDS);

            this.selectableObjectRegistrySynchronizer = new RegistrySynchronizer<String, RegistryTransformer, UnitConfigType.UnitConfig, UnitConfigType.UnitConfig.Builder>(
                    registryTransformerRegistry, getUnitRegistry().getUnitConfigRemoteRegistry(), RegistryTransformerFactory.getInstance()) {
                @Override
                public boolean verifyConfig(UnitConfigType.UnitConfig config) throws VerificationFailedException {
                    if (!scopeIdMap.values().contains(config.getId())) {
                        return false;
                    }
                    try {
                        if (hasLocationData(config)) {
                            return true;
                        } else {
                            throw new CouldNotPerformException("Registry Id found in the arguments, but no location data available.");
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        ExceptionPrinter.printHistory(ex, logger, LogLevel.ERROR);
                        return false;
                    } catch (CouldNotPerformException ex) {
                        ExceptionPrinter.printHistory(ex, logger, LogLevel.ERROR);
                        return false;
                    }
                }
            };

            Registries.waitForData();
            selectableObjectRegistrySynchronizer.activate();
        } catch (NotAvailableException ex) {
            throw new CouldNotPerformException("Could not connect to the registry.", ex);
        } catch (CouldNotPerformException ex) {
            throw new CouldNotPerformException("The RegistrySynchronization could not be activated although connection to the registry is possible.", ex);
        }
    }

    public static boolean hasLocationData(UnitConfigType.UnitConfig config) throws InterruptedException {
        try {
            AbstractUnitRemote unitRemote = (AbstractUnitRemote) Units.getUnit(config, false);
            unitRemote.getTransform3D();
            return true;
        } catch (CouldNotPerformException ex) {
            ExceptionPrinter.printHistory(ex, LOGGER, LogLevel.WARN);
            return false;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        /* Setup JPService */
        JPService.setApplicationName(SkeletonMerging.class);
        JPService.registerProperty(JPBaseScope.class);
        JPService.registerProperty(JPOutScope.class);
        JPService.registerProperty(JPLocalInput.class);
        JPService.registerProperty(JPLocalOutput.class);
        JPService.registerProperty(JPTransformFiles.class);
        JPService.registerProperty(JPRegistryIds.class);
//        JPService.registerProperty(JPRegistryId.class);
        JPService.parseAndExitOnError(args);
//        JPService.printHelp();

        SkeletonMerging app = new SkeletonMerging();
    }
}
