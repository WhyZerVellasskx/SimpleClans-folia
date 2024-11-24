package net.sacredlabyrinth.phaed.simpleclans.tasks;

import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;

import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.PERFORMANCE_SAVE_INTERVAL;

public class SaveDataTask {

	SimpleClans plugin = SimpleClans.getInstance();

	/**
	 * Starts the repetitive task
	 */
	public void start() {
		long interval = plugin.getSettingsManager().getMinutes(PERFORMANCE_SAVE_INTERVAL) * 20L; // интервал в тиках (1 тик = 1/20 сек)
		SimpleClans.getScheduler().runTaskTimer(this::run, interval, interval);
	}

	public void run() {
		plugin.getStorageManager().saveModified();
	}
}
