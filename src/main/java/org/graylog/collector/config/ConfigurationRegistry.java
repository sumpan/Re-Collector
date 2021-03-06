/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog.collector.config;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Service;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import org.graylog.collector.inputs.Input;
import org.graylog.collector.inputs.InputConfiguration;
import org.graylog.collector.inputs.InputService;
import org.graylog.collector.outputs.Output;
import org.graylog.collector.outputs.OutputConfiguration;
import org.graylog.collector.outputs.OutputService;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigurationRegistry {
    private final Set<Service> services = Sets.newHashSet();
    private final Set<Input> inputs = Sets.newHashSet();
    private final Set<Output> outputs = Sets.newHashSet();

    private final Map<String, InputConfiguration.Factory<? extends InputConfiguration>> inputConfigFactories;
    private final Map<String, OutputConfiguration.Factory<? extends OutputConfiguration>> outputConfigFactories;

    private final List<ConfigurationError> errors = Lists.newArrayList();
    private final ConfigurationValidator validator;

    @Inject
    public ConfigurationRegistry(Config config,
                                 Map<String, InputConfiguration.Factory<? extends InputConfiguration>> inputConfigs,
                                 Map<String, OutputConfiguration.Factory<? extends OutputConfiguration>> outputConfigs) {
        this.inputConfigFactories = inputConfigs;
        this.outputConfigFactories = outputConfigs;
        this.validator = new ConfigurationValidator();

        try {
            processConfig(config);
        } catch (ConfigException e) {
            errors.add(new ConfigurationError(e.getMessage()));
        }
    }

    private void processConfig(Config config) {
        final Config inputs = config.getConfig("inputs");
        final Config outputs = config.getConfig("outputs");

        buildInputs(inputs);
        buildOutputs(outputs);

        errors.addAll(validator.getErrors());
    }

    private void buildInputs(final Config inputConfigs) {
        final Map<String, InputConfiguration.Factory<? extends InputConfiguration>> factories = ConfigurationRegistry.this.inputConfigFactories;

        dispatchConfig(inputConfigs, (type, id, config) -> {
            if (factories.containsKey(type)) {
                final InputConfiguration cfg = factories.get(type).create(id, config);

                if (validator.isValid(cfg)) {
                    final InputService input = cfg.createInput();
                    services.add(input);
                    inputs.add(input);
                }
            } else {
                errors.add(new ConfigurationError("Unknown input type \"" + type + "\" for " + id));
            }
        });
    }

    private void buildOutputs(Config outputConfigs) {
        final Map<String, OutputConfiguration.Factory<? extends OutputConfiguration>> factories = ConfigurationRegistry.this.outputConfigFactories;

        dispatchConfig(outputConfigs, new ConfigCallback() {
            @Override
            public void call(String type, String id, Config config) {
                if (factories.containsKey(type)) {
                    final OutputConfiguration cfg = factories.get(type).create(id, config);

                    if (validator.isValid(cfg)) {
                        final OutputService output = cfg.createOutput();
                        services.add(output);
                        outputs.add(output);
                    }
                } else {
                    errors.add(new ConfigurationError("Unknown output type \"" + type + "\" for " + id));
                }
            }
        });
    }

    private void dispatchConfig(Config config, ConfigCallback callback) {
        for (Map.Entry<String, ConfigValue> entry : config.root().entrySet()) {
            final String id = entry.getKey();

            try {
                final Config entryConfig = ((ConfigObject) entry.getValue()).toConfig();
                final String type = entryConfig.getString("type");

                if (Strings.isNullOrEmpty(type)) {
                    errors.add(new ConfigurationError("Missing type field for " + id + " (" + entryConfig + ")"));
                    continue;
                }

                callback.call(type, id, entryConfig);
            } catch (ConfigException e) {
                errors.add(new ConfigurationError("[" + id + "] " + e.getMessage()));
            }
        }
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<ConfigurationError> getErrors() {
        return errors;
    }

    public Set<Service> getServices() {
        return services;
    }

    public Set<Input> getInputs() {
        return inputs;
    }

    public Set<Output> getOutputs() {
        return outputs;
    }

    private interface ConfigCallback {
        void call(String type, String id, Config config);
    }
}
