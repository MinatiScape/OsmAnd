package net.osmand.plus.keyevent.ui.keybindings;

import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.keyevent.InputDeviceHelper;
import net.osmand.plus.keyevent.KeyEventCategory;
import net.osmand.plus.keyevent.commands.KeyEventCommand;
import net.osmand.plus.keyevent.devices.InputDeviceProfile;
import net.osmand.plus.keyevent.ui.EditKeyActionFragment;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.util.Algorithms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ScreenController {

	private final OsmandApplication app;
	private final ApplicationMode appMode;
	private final InputDeviceHelper deviceHelper;
	private final InputDeviceProfile inputDevice;
	private FragmentActivity activity;

	public ScreenController(@NonNull OsmandApplication app,
	                        @NonNull ApplicationMode appMode) {
		this.app = app;
		this.appMode = appMode;
		this.deviceHelper = app.getInputDeviceHelper();
		this.inputDevice = deviceHelper.getSelectedDevice(appMode);
	}

	@NonNull
	public List<ScreenItem> populateScreenItems() {
		Map<KeyEventCategory, List<KeyAction>> categorizedActions = collectCategorizedActions();
		if (Algorithms.isEmpty(categorizedActions)) {
			return new ArrayList<>();
		}
		List<ScreenItem> screenItems = new ArrayList<>();
		for (KeyEventCategory category : KeyEventCategory.values()) {
			List<KeyAction> actions = categorizedActions.get(category);
			if (actions != null) {
				String categoryName = app.getString(category.getTitleId());
				screenItems.add(new ScreenItem(ScreenItemType.CARD_DIVIDER, categoryName));
				screenItems.add(new ScreenItem(ScreenItemType.HEADER, categoryName));
				for (KeyAction action : actions) {
					screenItems.add(new ScreenItem(ScreenItemType.ACTION_ITEM, action));
				}
			}
		}
		screenItems.add(new ScreenItem(ScreenItemType.CARD_BOTTOM_SHADOW));
		screenItems.add(new ScreenItem(ScreenItemType.SPACE));
		return screenItems;
	}

	@NonNull
	private Map<KeyEventCategory, List<KeyAction>> collectCategorizedActions() {
		if (inputDevice == null) {
			return new HashMap<>();
		}
		Map<KeyEventCategory, List<KeyAction>> categorizedActions = new HashMap<>();
		ArrayMap<Integer, KeyEventCommand> mappedCommands = inputDevice.getMappedCommands();
		for (int i = 0; i < mappedCommands.size(); i++) {
			Integer keyCode = mappedCommands.keyAt(i);
			KeyEventCommand command = mappedCommands.valueAt(i);
			if (command != null) {
				KeyEventCategory category = command.getCategory();
				List<KeyAction> actions = categorizedActions.get(category);
				if (actions == null) {
					actions = new ArrayList<>();
					categorizedActions.put(category, actions);
				}
				actions.add(new KeyAction(keyCode, command));
			}
		}
		return categorizedActions;
	}

	public void setActivity(FragmentActivity activity) {
		this.activity = activity;
	}

	public boolean isDeviceEditable() {
		return inputDevice != null && deviceHelper.isCustomDevice(inputDevice);
	}

	public void askEditKeyAction(KeyAction action) {
		if (inputDevice != null) {
			FragmentManager fm = activity.getSupportFragmentManager();
			EditKeyActionFragment.showInstance(fm, appMode, action, inputDevice.getId());
		}
	}
}
