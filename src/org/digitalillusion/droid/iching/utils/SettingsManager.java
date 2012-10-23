package org.digitalillusion.droid.iching.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.List;

import org.digitalillusion.droid.iching.IChingActivity;
import org.digitalillusion.droid.iching.utils.lists.SettingsEntry;


/**
 * Façade for the settings map
 * 
 * @author digitalillusion
 */
public class SettingsManager {

	/** Internal storage of the settings **/
	private HashMap<String, Serializable> settingsMap = new HashMap<String, Serializable>();
	
	/**
	 * The enumeration of available settings
	 */
	public enum SETTINGS_MAP {
		HAPTIC_FEEDBACK("hapticFeedback"),
		CHANGING_LINES_EVALUATOR("changingLinesEvaluator"),
		LANGUAGE("language"),
		DICTIONARY("dictionary");
		
		
		private String key;
		
		SETTINGS_MAP(String key) {
			this.key = key;
		}
		
		public String getKey() {
			return key;
		}
	}
	
	/**
	 * @see 
	 * {@link HashMap#get(Object) }
	 */
	public Serializable get(SETTINGS_MAP setting) {
		Serializable entry = settingsMap.get(setting.getKey());
		if (entry == null) {
			entry = getDefault(setting);
		}
		return entry;
	}
	
	/**
	 * @see 
	 * {@link HashMap#put(Object) }
	 */
	public Serializable put(SETTINGS_MAP setting, Serializable object ) {
		return settingsMap.put(setting.getKey(), object);
	}
	
	/**
	 * @param activity The caller activity, needed to display popups (eventually)
	 * @see {@link DataPersister#saveOptions(HashMap, android.app.Activity) }
	 */
	public void save(IChingActivity activity) {
		DataPersister.saveOptions(settingsMap, activity);
	}
	
	/**
	 * @see {@link DataPersister#loadOptions(HashMap)}
	 */
	public void load() throws FileNotFoundException, IOException {
		DataPersister.loadOptions(settingsMap);
	}
	
	/**
	 * Load the default settings
	 */
    public void resetDefaults() {
    	settingsMap.put(SETTINGS_MAP.HAPTIC_FEEDBACK.getKey(), getDefault(SETTINGS_MAP.HAPTIC_FEEDBACK));
		settingsMap.put(SETTINGS_MAP.CHANGING_LINES_EVALUATOR.getKey(), getDefault(SETTINGS_MAP.CHANGING_LINES_EVALUATOR));
		settingsMap.put(SETTINGS_MAP.LANGUAGE.getKey(), getDefault(SETTINGS_MAP.LANGUAGE));
		settingsMap.put(SETTINGS_MAP.DICTIONARY.getKey(), getDefault(SETTINGS_MAP.DICTIONARY));
	}
    
    /**
     * Return a default setting
     * 
     * @param setting The setting to retrieve
     * @return The default value of the requested setting
     */
    public Serializable getDefault(SETTINGS_MAP setting) throws InvalidParameterException {
		if (setting.equals(SETTINGS_MAP.HAPTIC_FEEDBACK)) {
			return Consts.HAPTIC_FEEDBACK_ON_THROW_COINS;
     	} else if (setting.equals(SETTINGS_MAP.CHANGING_LINES_EVALUATOR)) {
     		return Consts.EVALUATOR_MASTERYIN;
     	} else if (setting.equals(SETTINGS_MAP.LANGUAGE)) {
    		return Consts.LANGUAGE_EN;
    	} else if (setting.equals(SETTINGS_MAP.DICTIONARY)) {
    		return Consts.DICTIONARY_ALTERVISTA;
    	}
    	throw new InvalidParameterException(setting.getKey() + " does not specify an option.");
    }
    
    /**
     * Create a new option, aka a group of settings
     * @param <T> The type of the settings
     * @param optionsList The list of options to which this option has to be add
     * @param name A name for the option
     * @param values The possible settings for this option
     * @param defaultValue The default setting for this option
     */
    @SuppressWarnings("unchecked")
	public <T extends Serializable> void createOption(List<SettingsEntry<?>> optionsList, String name, T[] values, SETTINGS_MAP defaultValue) {
    	SettingsEntry<T> newOption = new SettingsEntry<T>();
    	for (T val : values) {
    		newOption.addOptionValue(val);
    	}
		newOption.setOptionName(name);
		newOption.setOptionValue((T) get(defaultValue));
		optionsList.add(newOption);
    }
}