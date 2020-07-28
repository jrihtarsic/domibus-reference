package eu.domibus.ext.services;

import java.util.Map;

/**
 * This is the command logic used by a plugin that will be executed in case of a cluster deployment.
 *
 * @author Cosmin Baciu
 * @since 4.2
 */
public interface CommandExtTask {

    /**
     * Checks if this command is able to process the command with a specific name
     *
     * @param command The command name
     * @return True if this command task can handle the command
     */
    boolean canHandle(String command);

    /**
     * Executes the command in a cluster deployment.
     *
     * @param properties The command properties
     */
    void execute(Map<String, String> properties);
}
