package net.osmand.plus.settings.backend.backup;

import static net.osmand.plus.settings.backend.backup.items.FileSettingsItem.FileSubtype;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.Collator;
import net.osmand.IndexConstants;
import net.osmand.OsmAndCollator;
import net.osmand.PlatformUtil;
import net.osmand.data.LatLon;
import net.osmand.map.ITileSource;
import net.osmand.map.TileSourceManager;
import net.osmand.map.TileSourceManager.TileSourceTemplate;
import net.osmand.map.WorldRegion;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.download.local.LocalIndexHelper;
import net.osmand.plus.download.local.LocalItem;
import net.osmand.plus.download.local.LocalItemType;
import net.osmand.plus.helpers.AvoidSpecificRoads.AvoidRoadInfo;
import net.osmand.plus.helpers.FileNameTranslationHelper;
import net.osmand.plus.helpers.SearchHistoryHelper;
import net.osmand.plus.helpers.SearchHistoryHelper.HistoryEntry;
import net.osmand.plus.mapmarkers.ItineraryType;
import net.osmand.plus.mapmarkers.MapMarker;
import net.osmand.plus.mapmarkers.MapMarkersGroup;
import net.osmand.plus.myplaces.favorites.FavoriteGroup;
import net.osmand.plus.onlinerouting.engine.OnlineRoutingEngine;
import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.plugins.audionotes.AudioVideoNotesPlugin;
import net.osmand.plus.plugins.audionotes.AudioVideoNotesPlugin.Recording;
import net.osmand.plus.plugins.development.OsmandDevelopmentPlugin;
import net.osmand.plus.plugins.osmedit.OsmEditingPlugin;
import net.osmand.plus.plugins.osmedit.data.OpenstreetmapPoint;
import net.osmand.plus.plugins.osmedit.data.OsmNotesPoint;
import net.osmand.plus.poi.PoiUIFilter;
import net.osmand.plus.quickaction.QuickAction;
import net.osmand.plus.quickaction.QuickActionRegistry;
import net.osmand.plus.resources.SQLiteTileSource;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.ApplicationModeBean;
import net.osmand.plus.settings.backend.ExportSettingsCategory;
import net.osmand.plus.settings.backend.ExportType;
import net.osmand.plus.settings.backend.backup.items.AvoidRoadsSettingsItem;
import net.osmand.plus.settings.backend.backup.items.FavoritesSettingsItem;
import net.osmand.plus.settings.backend.backup.items.FileSettingsItem;
import net.osmand.plus.settings.backend.backup.items.GlobalSettingsItem;
import net.osmand.plus.settings.backend.backup.items.GpxSettingsItem;
import net.osmand.plus.settings.backend.backup.items.HistoryMarkersSettingsItem;
import net.osmand.plus.settings.backend.backup.items.ItinerarySettingsItem;
import net.osmand.plus.settings.backend.backup.items.MapSourcesSettingsItem;
import net.osmand.plus.settings.backend.backup.items.MarkersSettingsItem;
import net.osmand.plus.settings.backend.backup.items.NavigationHistorySettingsItem;
import net.osmand.plus.settings.backend.backup.items.OnlineRoutingSettingsItem;
import net.osmand.plus.settings.backend.backup.items.OsmEditsSettingsItem;
import net.osmand.plus.settings.backend.backup.items.OsmNotesSettingsItem;
import net.osmand.plus.settings.backend.backup.items.PoiUiFiltersSettingsItem;
import net.osmand.plus.settings.backend.backup.items.ProfileSettingsItem;
import net.osmand.plus.settings.backend.backup.items.QuickActionsSettingsItem;
import net.osmand.plus.settings.backend.backup.items.SearchHistorySettingsItem;
import net.osmand.plus.settings.backend.backup.items.SettingsItem;
import net.osmand.plus.settings.enums.HistorySource;
import net.osmand.plus.settings.fragments.SettingsCategoryItems;
import net.osmand.plus.track.helpers.GpxDataItem;
import net.osmand.util.Algorithms;

import org.apache.commons.logging.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class SettingsHelper {

	public static final int VERSION = 1;

	public static final String EXPORT_TYPE_LIST_KEY = "export_type_list_key";
	public static final String REPLACE_KEY = "replace";
	public static final String SILENT_IMPORT_KEY = "silent_import";
	public static final String SETTINGS_LATEST_CHANGES_KEY = "settings_latest_changes";
	public static final String SETTINGS_VERSION_KEY = "settings_version";

	public static final int BUFFER = 1024;

	public static final Log LOG = PlatformUtil.getLog(SettingsHelper.class);

	private final OsmandApplication app;

	public interface CollectListener {
		void onCollectFinished(boolean succeed, boolean empty, @NonNull List<SettingsItem> items);
	}

	public interface ImportListener {

		default void onImportProgressUpdate(int value, int uploadedKb) {

		}

		default void onImportItemStarted(@NonNull String type, @NonNull String fileName, int work) {

		}

		default void onImportItemProgress(@NonNull String type, @NonNull String fileName, int value) {

		}

		default void onImportItemFinished(@NonNull String type, @NonNull String fileName) {

		}

		default void onImportFinished(boolean succeed, boolean needRestart, @NonNull List<SettingsItem> items) {

		}
	}

	public interface CheckDuplicatesListener {
		void onDuplicatesChecked(@NonNull List<Object> duplicates, List<SettingsItem> items);
	}

	public interface ExportProgressListener {
		void updateProgress(int value);
	}

	public enum ImportType {
		COLLECT,
		COLLECT_AND_READ,
		CHECK_DUPLICATES,
		IMPORT,
		IMPORT_FORCE_READ,
	}

	public SettingsHelper(@NonNull OsmandApplication app) {
		this.app = app;
	}

	public OsmandApplication getApp() {
		return app;
	}

	public List<SettingsItem> getFilteredSettingsItems(List<ExportType> acceptedTypes,
	                                                   boolean export, boolean addEmptyItems, boolean offlineBackup) {
		Map<ExportType, List<?>> typesMap = new HashMap<>();
		typesMap.putAll(getSettingsItems(acceptedTypes, addEmptyItems));
		typesMap.putAll(getMyPlacesItems(acceptedTypes, addEmptyItems));
		typesMap.putAll(getResourcesItems(acceptedTypes, addEmptyItems, offlineBackup));
		return getFilteredSettingsItems(typesMap, acceptedTypes, Collections.emptyList(), export);
	}

	public List<SettingsItem> getFilteredSettingsItems(@NonNull Map<ExportType, List<?>> allSettingsMap,
	                                                   @NonNull List<ExportType> settingsTypes,
	                                                   @NonNull List<SettingsItem> settingsItems,
	                                                   boolean export) {
		List<SettingsItem> filteredSettingsItems = new ArrayList<>();
		for (ExportType settingsType : settingsTypes) {
			List<?> settingsDataObjects = allSettingsMap.get(settingsType);
			if (settingsDataObjects != null) {
				filteredSettingsItems.addAll(prepareSettingsItems(settingsDataObjects, settingsItems, export));
			}
		}
		return filteredSettingsItems;
	}

	public Map<ExportSettingsCategory, SettingsCategoryItems> getSettingsByCategory(boolean addEmptyItems, boolean offlineBackup) {
		Map<ExportSettingsCategory, SettingsCategoryItems> dataList = new LinkedHashMap<>();

		Map<ExportType, List<?>> settingsItems = getSettingsItems(null, addEmptyItems);
		Map<ExportType, List<?>> myPlacesItems = getMyPlacesItems(null, addEmptyItems);
		Map<ExportType, List<?>> resourcesItems = getResourcesItems(null, addEmptyItems, offlineBackup);

		if (!settingsItems.isEmpty() || addEmptyItems) {
			dataList.put(ExportSettingsCategory.SETTINGS, new SettingsCategoryItems(settingsItems));
		}
		if (!myPlacesItems.isEmpty() || addEmptyItems) {
			dataList.put(ExportSettingsCategory.MY_PLACES, new SettingsCategoryItems(myPlacesItems));
		}
		if (!resourcesItems.isEmpty() || addEmptyItems) {
			dataList.put(ExportSettingsCategory.RESOURCES, new SettingsCategoryItems(resourcesItems));
		}

		return dataList;
	}

	private Map<ExportType, List<?>> getSettingsItems(@Nullable List<ExportType> acceptedTypes,
	                                                  boolean addEmptyItems) {
		Map<ExportType, List<?>> settingsItems = new LinkedHashMap<>();

		if (acceptedTypes == null || acceptedTypes.contains(ExportType.PROFILE)) {
			List<ApplicationModeBean> appModeBeans = new ArrayList<>();
			for (ApplicationMode mode : ApplicationMode.allPossibleValues()) {
				appModeBeans.add(mode.toModeBean());
			}
			settingsItems.put(ExportType.PROFILE, appModeBeans);
		}
		List<GlobalSettingsItem> globalSettingsList = acceptedTypes == null || acceptedTypes.contains(ExportType.GLOBAL)
				? Collections.singletonList(new GlobalSettingsItem(app.getSettings()))
				: Collections.emptyList();
		if (!globalSettingsList.isEmpty() || addEmptyItems) {
			settingsItems.put(ExportType.GLOBAL, globalSettingsList);
		}
		QuickActionRegistry registry = app.getQuickActionRegistry();
		List<QuickAction> actionsList = acceptedTypes == null || acceptedTypes.contains(ExportType.QUICK_ACTIONS)
				? registry.getQuickActions()
				: Collections.emptyList();
		if (!actionsList.isEmpty() || addEmptyItems) {
			settingsItems.put(ExportType.QUICK_ACTIONS, actionsList);
		}
		List<PoiUIFilter> poiList = acceptedTypes == null || acceptedTypes.contains(ExportType.POI_TYPES)
				? app.getPoiFilters().getUserDefinedPoiFilters(false)
				: Collections.emptyList();
		if (!poiList.isEmpty() || addEmptyItems) {
			settingsItems.put(ExportType.POI_TYPES, poiList);
		}
		Map<LatLon, AvoidRoadInfo> impassableRoads = acceptedTypes == null || acceptedTypes.contains(ExportType.AVOID_ROADS)
				? app.getAvoidSpecificRoads().getImpassableRoads()
				: Collections.emptyMap();
		if (!impassableRoads.isEmpty() || addEmptyItems) {
			settingsItems.put(ExportType.AVOID_ROADS, new ArrayList<>(impassableRoads.values()));
		}
		return settingsItems;
	}

	private Map<ExportType, List<?>> getMyPlacesItems(@Nullable List<ExportType> acceptedTypes,
	                                                  boolean addEmptyItems) {
		Map<ExportType, List<?>> myPlacesItems = new LinkedHashMap<>();

		List<FavoriteGroup> favoriteGroups = acceptedTypes == null || acceptedTypes.contains(ExportType.FAVORITES)
				? app.getFavoritesHelper().getFavoriteGroups()
				: Collections.emptyList();
		if (!favoriteGroups.isEmpty() || addEmptyItems) {
			myPlacesItems.put(ExportType.FAVORITES, favoriteGroups);
		}
		List<GpxDataItem> gpxItems = acceptedTypes == null || acceptedTypes.contains(ExportType.TRACKS)
				? app.getGpxDbHelper().getItems()
				: Collections.emptyList();
		if (!gpxItems.isEmpty() || addEmptyItems) {
			List<File> files = new ArrayList<>();
			for (GpxDataItem gpxItem : gpxItems) {
				File file = gpxItem.getFile();
				if (file.exists() && !file.isDirectory()) {
					files.add(file);
				}
			}
			if (!files.isEmpty() || addEmptyItems) {
				myPlacesItems.put(ExportType.TRACKS, files);
			}
		}
		OsmEditingPlugin osmEditingPlugin = PluginsHelper.getActivePlugin(OsmEditingPlugin.class);
		if (osmEditingPlugin != null) {
			List<OsmNotesPoint> notesPointList = acceptedTypes == null || acceptedTypes.contains(ExportType.OSM_NOTES)
					? osmEditingPlugin.getDBBug().getOsmBugsPoints()
					: Collections.emptyList();
			if (!notesPointList.isEmpty() || addEmptyItems) {
				myPlacesItems.put(ExportType.OSM_NOTES, notesPointList);
			}
			List<OpenstreetmapPoint> editsPointList = acceptedTypes == null || acceptedTypes.contains(ExportType.OSM_EDITS)
					? osmEditingPlugin.getDBPOI().getOpenstreetmapPoints()
					: Collections.emptyList();
			if (!editsPointList.isEmpty() || addEmptyItems) {
				myPlacesItems.put(ExportType.OSM_EDITS, editsPointList);
			}
		}
		AudioVideoNotesPlugin avNotesPlugin = PluginsHelper.getActivePlugin(AudioVideoNotesPlugin.class);
		if (avNotesPlugin != null) {
			List<File> files = new ArrayList<>();
			if (acceptedTypes == null || acceptedTypes.contains(ExportType.MULTIMEDIA_NOTES)) {
				for (Recording rec : avNotesPlugin.getAllRecordings()) {
					File file = rec.getFile();
					if (file != null && file.exists()) {
						files.add(file);
					}
				}
			}
			if (!files.isEmpty() || addEmptyItems) {
				myPlacesItems.put(ExportType.MULTIMEDIA_NOTES, files);
			}
		}
		List<MapMarker> mapMarkers = acceptedTypes == null || acceptedTypes.contains(ExportType.ACTIVE_MARKERS)
				? app.getMapMarkersHelper().getMapMarkers()
				: Collections.emptyList();
		if (!mapMarkers.isEmpty() || addEmptyItems) {
			if (mapMarkers.isEmpty()) {
				myPlacesItems.put(ExportType.ACTIVE_MARKERS, Collections.emptyList());
			} else {
				String name = app.getString(R.string.map_markers);
				String groupId = ExportType.ACTIVE_MARKERS.name();
				MapMarkersGroup markersGroup = new MapMarkersGroup(groupId, name, ItineraryType.MARKERS);
				markersGroup.setMarkers(mapMarkers);
				myPlacesItems.put(ExportType.ACTIVE_MARKERS, Collections.singletonList(markersGroup));
			}
		}
		List<MapMarker> markersHistory = acceptedTypes == null || acceptedTypes.contains(ExportType.HISTORY_MARKERS)
				? app.getMapMarkersHelper().getMapMarkersHistory()
				: Collections.emptyList();
		if (!markersHistory.isEmpty() || addEmptyItems) {
			if (markersHistory.isEmpty()) {
				myPlacesItems.put(ExportType.HISTORY_MARKERS, Collections.emptyList());
			} else {
				String name = app.getString(R.string.shared_string_history);
				String groupId = ExportType.HISTORY_MARKERS.name();
				MapMarkersGroup markersGroup = new MapMarkersGroup(groupId, name, ItineraryType.MARKERS);
				markersGroup.setMarkers(markersHistory);
				myPlacesItems.put(ExportType.HISTORY_MARKERS, Collections.singletonList(markersGroup));
			}
		}
		List<HistoryEntry> searchHistoryEntries = acceptedTypes == null || acceptedTypes.contains(ExportType.SEARCH_HISTORY)
				? SearchHistoryHelper.getInstance(app).getHistoryEntries(HistorySource.SEARCH, false)
				: Collections.emptyList();
		if (!searchHistoryEntries.isEmpty() || addEmptyItems) {
			myPlacesItems.put(ExportType.SEARCH_HISTORY, searchHistoryEntries);
		}
		List<HistoryEntry> navigationHistoryEntries = acceptedTypes == null || acceptedTypes.contains(ExportType.NAVIGATION_HISTORY)
				? SearchHistoryHelper.getInstance(app).getHistoryEntries(HistorySource.NAVIGATION, false)
				: Collections.emptyList();
		if (!navigationHistoryEntries.isEmpty() || addEmptyItems) {
			myPlacesItems.put(ExportType.NAVIGATION_HISTORY, navigationHistoryEntries);
		}
		List<MapMarkersGroup> markersGroups = acceptedTypes == null || acceptedTypes.contains(ExportType.ITINERARY_GROUPS)
				? app.getMapMarkersHelper().getVisibleMapMarkersGroups()
				: Collections.emptyList();
		if (!markersGroups.isEmpty() || addEmptyItems) {
			myPlacesItems.put(ExportType.ITINERARY_GROUPS, markersGroups);
		}
		return myPlacesItems;
	}

	private Map<ExportType, List<?>> getResourcesItems(@Nullable List<ExportType> acceptedTypes,
	                                                   boolean addEmptyItems, boolean offlineBackup) {
		Map<ExportType, List<?>> resourcesItems = new LinkedHashMap<>();

		Map<String, File> externalRenderers = acceptedTypes == null || acceptedTypes.contains(ExportType.CUSTOM_RENDER_STYLE)
				? app.getRendererRegistry().getExternalRenderers()
				: Collections.emptyMap();
		if (!externalRenderers.isEmpty() || addEmptyItems) {
			resourcesItems.put(ExportType.CUSTOM_RENDER_STYLE, new ArrayList<>(externalRenderers.values()));
		}
		List<File> routingProfiles = new ArrayList<>();
		if (acceptedTypes == null || acceptedTypes.contains(ExportType.CUSTOM_ROUTING)) {
			File routingProfilesFolder = app.getAppPath(IndexConstants.ROUTING_PROFILES_DIR);
			if (routingProfilesFolder.exists() && routingProfilesFolder.isDirectory()) {
				File[] fl = routingProfilesFolder.listFiles();
				if (fl != null && fl.length > 0) {
					routingProfiles.addAll(Arrays.asList(fl));
				}
			}
		}
		if (!Algorithms.isEmpty(routingProfiles) || addEmptyItems) {
			resourcesItems.put(ExportType.CUSTOM_ROUTING, routingProfiles);
		}
		List<OnlineRoutingEngine> onlineRoutingEngines = acceptedTypes == null || acceptedTypes.contains(ExportType.ONLINE_ROUTING_ENGINES)
				? app.getOnlineRoutingHelper().getOnlyCustomEngines()
				: Collections.emptyList();
		if (!Algorithms.isEmpty(onlineRoutingEngines) || addEmptyItems) {
			resourcesItems.put(ExportType.ONLINE_ROUTING_ENGINES, onlineRoutingEngines);
		}
		List<ITileSource> iTileSources = new ArrayList<>();
		if (acceptedTypes == null || acceptedTypes.contains(ExportType.MAP_SOURCES)) {
			Set<String> tileSourceNames = app.getSettings().getTileSourceEntries(true).keySet();
			for (String name : tileSourceNames) {
				File file = app.getAppPath(IndexConstants.TILES_INDEX_DIR + name);
				ITileSource template;
				if (file.getName().endsWith(SQLiteTileSource.EXT)) {
					template = new SQLiteTileSource(app, file, TileSourceManager.getKnownSourceTemplates());
				} else {
					template = TileSourceManager.createTileSourceTemplate(file);
				}
				if (template.getUrlTemplate() != null) {
					iTileSources.add(template);
				}
			}
		}
		if (!iTileSources.isEmpty() || addEmptyItems) {
			resourcesItems.put(ExportType.MAP_SOURCES, iTileSources);
		}

		collectResourcesForLocalTypes(resourcesItems, acceptedTypes, addEmptyItems, Arrays.asList(
				LocalItemType.MAP_DATA,
				LocalItemType.WIKI_AND_TRAVEL_MAPS,
				LocalItemType.DEPTH_DATA,
				LocalItemType.ROAD_DATA,
				LocalItemType.TERRAIN_DATA,
				LocalItemType.TTS_VOICE_DATA,
				LocalItemType.VOICE_DATA
		));
		if (PluginsHelper.isEnabled(OsmandDevelopmentPlugin.class) && offlineBackup) {
			List<File> files = app.getFavoritesHelper().getFileHelper().getBackupFiles();
			if (!files.isEmpty() || addEmptyItems) {
				resourcesItems.put(ExportType.FAVORITES_BACKUP, files);
			}
		}
		return resourcesItems;
	}

	private void collectResourcesForLocalTypes(@NonNull Map<ExportType, List<?>> resources,
	                                           @Nullable List<ExportType> acceptedTypes,
	                                           boolean addEmptyItems,
	                                           @NonNull List<LocalItemType> localTypes) {
		List<LocalItem> filteredLocalItems = getFilteredLocalItems(localTypes, acceptedTypes);
		for (LocalItemType localType : localTypes) {
			List<File> files = getFilesByType(filteredLocalItems, localType);
			if (!files.isEmpty() || addEmptyItems) {
				if (!Algorithms.equalsToAny(localType, LocalItemType.TTS_VOICE_DATA, LocalItemType.VOICE_DATA)) {
					sortLocalFiles(files);
				}
				resources.put(ExportType.findBy(localType), files);
			}
		}
	}

	@NonNull
	private List<LocalItem> getFilteredLocalItems(@NonNull List<LocalItemType> localTypes,
	                                              @Nullable List<ExportType> acceptedTypes) {
		List<LocalItemType> filteredLocalTypes = getFilteredLocalTypes(localTypes, acceptedTypes);
		if (Algorithms.isEmpty(filteredLocalTypes)) {
			return Collections.emptyList();
		}
		LocalIndexHelper indexHelper = new LocalIndexHelper(app);
		LocalItemType[] localItemTypes = localTypes.toArray(new LocalItemType[0]);
		List<LocalItem> localIndexes = indexHelper.getLocalIndexItems(true, false, null, localItemTypes);
		if (Algorithms.containsAny(localTypes, LocalItemType.MAP_DATA, LocalItemType.WIKI_AND_TRAVEL_MAPS)) {
			filterDefaultLocalItems(localIndexes);
		}
		return localIndexes;
	}

	@NonNull
	private List<LocalItemType> getFilteredLocalTypes(@NonNull List<LocalItemType> localTypes,
	                                                  @Nullable List<ExportType> acceptedTypes) {
		List<LocalItemType> result = new ArrayList<>(localTypes);
		if (acceptedTypes != null) {
			Iterator<LocalItemType> iterator = result.iterator();
			while (iterator.hasNext()) {
				if (!acceptedTypes.contains(ExportType.findBy(iterator.next()))) {
					iterator.remove();
				}
			}
		}
		return result;
	}

	private void filterDefaultLocalItems(@NonNull List<LocalItem> localItems) {
		String baseMini = WorldRegion.WORLD_BASEMAP_MINI + IndexConstants.BINARY_MAP_INDEX_EXT;
		String defaultWikivoyage = "Default_wikivoyage" + IndexConstants.BINARY_TRAVEL_GUIDE_MAP_INDEX_EXT;

		Iterator<LocalItem> iterator = localItems.iterator();
		while (iterator.hasNext()) {
			LocalItem localItem = iterator.next();
			boolean isBaseMini = localItem.getType() == LocalItemType.MAP_DATA && baseMini.equalsIgnoreCase(localItem.getFileName());
			boolean isDefaultWikivoyage = localItem.getType() == LocalItemType.WIKI_AND_TRAVEL_MAPS && defaultWikivoyage.equalsIgnoreCase(localItem.getFileName());
			if (isBaseMini || isDefaultWikivoyage) {
				iterator.remove();
			}
		}
	}

	@NonNull
	private List<File> getFilesByType(@NonNull List<LocalItem> localItems, LocalItemType... types) {
		List<File> files = new ArrayList<>();
		for (LocalItem info : localItems) {
			File file = new File(info.getPath());
			boolean filtered = false;
			for (LocalItemType type : types) {
				if (info.getType() == type) {
					filtered = true;
					break;
				}
			}
			if (file.exists() && filtered) {
				files.add(file);
			}
		}
		return files;
	}

	public List<SettingsItem> prepareSettingsItems(List<?> data, List<SettingsItem> settingsItems, boolean export) {
		List<SettingsItem> result = new ArrayList<>();
		List<QuickAction> quickActions = new ArrayList<>();
		List<PoiUIFilter> poiUIFilters = new ArrayList<>();
		List<ITileSource> tileSourceTemplates = new ArrayList<>();
		List<AvoidRoadInfo> avoidRoads = new ArrayList<>();
		List<ApplicationModeBean> appModeBeans = new ArrayList<>();
		List<FavoriteGroup> favoriteGroups = new ArrayList<>();
		List<OsmNotesPoint> osmNotesPointList = new ArrayList<>();
		List<OpenstreetmapPoint> osmEditsPointList = new ArrayList<>();
		List<MapMarkersGroup> markersGroups = new ArrayList<>();
		List<MapMarkersGroup> markersHistoryGroups = new ArrayList<>();
		List<HistoryEntry> historySearchEntries = new ArrayList<>();
		List<HistoryEntry> historyNavigationEntries = new ArrayList<>();
		List<OnlineRoutingEngine> onlineRoutingEngines = new ArrayList<>();
		List<MapMarkersGroup> itineraryGroups = new ArrayList<>();

		for (Object object : data) {
			if (object instanceof QuickAction) {
				quickActions.add((QuickAction) object);
			} else if (object instanceof PoiUIFilter) {
				poiUIFilters.add((PoiUIFilter) object);
			} else if (object instanceof TileSourceTemplate || object instanceof SQLiteTileSource) {
				tileSourceTemplates.add((ITileSource) object);
			} else if (object instanceof File) {
				try {
					File file = (File) object;
					if (file.getName().endsWith(IndexConstants.GPX_FILE_EXT)) {
						result.add(new GpxSettingsItem(app, file));
					} else {
						result.add(new FileSettingsItem(app, file));
					}
				} catch (IllegalArgumentException e) {
					LOG.warn("Trying to export unsupported file type", e);
				}
			} else if (object instanceof FileSettingsItem) {
				result.add((FileSettingsItem) object);
			} else if (object instanceof AvoidRoadInfo) {
				avoidRoads.add((AvoidRoadInfo) object);
			} else if (object instanceof ApplicationModeBean) {
				appModeBeans.add((ApplicationModeBean) object);
			} else if (object instanceof OsmNotesPoint) {
				osmNotesPointList.add((OsmNotesPoint) object);
			} else if (object instanceof OpenstreetmapPoint) {
				osmEditsPointList.add((OpenstreetmapPoint) object);
			} else if (object instanceof FavoriteGroup) {
				favoriteGroups.add((FavoriteGroup) object);
			} else if (object instanceof MapMarkersGroup) {
				MapMarkersGroup markersGroup = (MapMarkersGroup) object;
				if (ExportType.ACTIVE_MARKERS.name().equals(markersGroup.getId())) {
					markersGroups.add((MapMarkersGroup) object);
				} else if (ExportType.HISTORY_MARKERS.name().equals(markersGroup.getId())) {
					markersHistoryGroups.add((MapMarkersGroup) object);
				} else {
					itineraryGroups.add((MapMarkersGroup) object);
				}
			} else if (object instanceof HistoryEntry) {
				HistoryEntry entry = (HistoryEntry) object;
				if (entry.getSource() == HistorySource.NAVIGATION) {
					historyNavigationEntries.add(entry);
				} else {
					historySearchEntries.add(entry);
				}
			} else if (object instanceof GlobalSettingsItem) {
				result.add((GlobalSettingsItem) object);
			} else if (object instanceof OnlineRoutingEngine) {
				onlineRoutingEngines.add((OnlineRoutingEngine) object);
			}
		}
		if (!quickActions.isEmpty()) {
			QuickActionsSettingsItem baseItem = getBaseItem(SettingsItemType.QUICK_ACTIONS, QuickActionsSettingsItem.class, settingsItems);
			result.add(new QuickActionsSettingsItem(app, baseItem, quickActions));
		}
		if (!poiUIFilters.isEmpty()) {
			PoiUiFiltersSettingsItem baseItem = getBaseItem(SettingsItemType.POI_UI_FILTERS, PoiUiFiltersSettingsItem.class, settingsItems);
			result.add(new PoiUiFiltersSettingsItem(app, baseItem, poiUIFilters));
		}
		if (!tileSourceTemplates.isEmpty()) {
			MapSourcesSettingsItem baseItem = getBaseItem(SettingsItemType.MAP_SOURCES, MapSourcesSettingsItem.class, settingsItems);
			result.add(new MapSourcesSettingsItem(app, baseItem, tileSourceTemplates));
		}
		if (!avoidRoads.isEmpty()) {
			AvoidRoadsSettingsItem baseItem = getBaseItem(SettingsItemType.AVOID_ROADS, AvoidRoadsSettingsItem.class, settingsItems);
			result.add(new AvoidRoadsSettingsItem(app, baseItem, avoidRoads));
		}
		if (!appModeBeans.isEmpty()) {
			for (ApplicationModeBean modeBean : appModeBeans) {
				if (export) {
					ApplicationMode mode = ApplicationMode.valueOfStringKey(modeBean.stringKey, null);
					if (mode != null) {
						result.add(new ProfileSettingsItem(app, mode));
					}
				} else {
					result.add(new ProfileSettingsItem(app, getBaseProfileSettingsItem(modeBean, settingsItems), modeBean));
				}
			}
		}
		if (!osmNotesPointList.isEmpty()) {
			OsmNotesSettingsItem baseItem = getBaseItem(SettingsItemType.OSM_NOTES, OsmNotesSettingsItem.class, settingsItems);
			result.add(new OsmNotesSettingsItem(app, baseItem, osmNotesPointList));
		}
		if (!osmEditsPointList.isEmpty()) {
			OsmEditsSettingsItem baseItem = getBaseItem(SettingsItemType.OSM_EDITS, OsmEditsSettingsItem.class, settingsItems);
			result.add(new OsmEditsSettingsItem(app, baseItem, osmEditsPointList));
		}
		if (!favoriteGroups.isEmpty()) {
			if (export) {
				for (FavoriteGroup favoriteGroup : favoriteGroups) {
					result.add(new FavoritesSettingsItem(app, null, Collections.singletonList(favoriteGroup)));
				}
			} else {
				boolean hasGroupFile = false;
				for (FavoriteGroup favoriteGroup : favoriteGroups) {
					FavoritesSettingsItem favSettingsItem = null;
					for (SettingsItem item : settingsItems) {
						String fileName = item.getFileName();
						if (item instanceof FavoritesSettingsItem
								&& app.getFavoritesHelper().getFileHelper().getExternalFile(favoriteGroup).getName().equals(fileName)) {
							favSettingsItem = (FavoritesSettingsItem) item;
							break;
						}
					}
					if (favSettingsItem != null) {
						result.add(new FavoritesSettingsItem(app, favSettingsItem, Collections.singletonList(favoriteGroup)));
						hasGroupFile = true;
					}
				}
				if (!hasGroupFile) {
					FavoritesSettingsItem baseItem = getBaseItem(SettingsItemType.FAVOURITES, FavoritesSettingsItem.class, settingsItems);
					result.add(new FavoritesSettingsItem(app, baseItem, favoriteGroups));
				}
			}
		}
		if (!markersGroups.isEmpty()) {
			List<MapMarker> mapMarkers = new ArrayList<>();
			for (MapMarkersGroup group : markersGroups) {
				mapMarkers.addAll(group.getMarkers());
			}
			MarkersSettingsItem baseItem = getBaseItem(SettingsItemType.ACTIVE_MARKERS, MarkersSettingsItem.class, settingsItems);
			result.add(new MarkersSettingsItem(app, baseItem, mapMarkers));
		}
		if (!markersHistoryGroups.isEmpty()) {
			List<MapMarker> mapMarkers = new ArrayList<>();
			for (MapMarkersGroup group : markersHistoryGroups) {
				mapMarkers.addAll(group.getMarkers());
			}
			HistoryMarkersSettingsItem baseItem = getBaseItem(SettingsItemType.HISTORY_MARKERS, HistoryMarkersSettingsItem.class, settingsItems);
			result.add(new HistoryMarkersSettingsItem(app, baseItem, mapMarkers));
		}
		if (!historySearchEntries.isEmpty()) {
			SearchHistorySettingsItem baseItem = getBaseItem(SettingsItemType.SEARCH_HISTORY, SearchHistorySettingsItem.class, settingsItems);
			result.add(new SearchHistorySettingsItem(app, baseItem, historySearchEntries));
		}
		if (!historyNavigationEntries.isEmpty()) {
			NavigationHistorySettingsItem baseItem = getBaseItem(SettingsItemType.NAVIGATION_HISTORY, NavigationHistorySettingsItem.class, settingsItems);
			result.add(new NavigationHistorySettingsItem(app, baseItem, historyNavigationEntries));
		}
		if (!onlineRoutingEngines.isEmpty()) {
			OnlineRoutingSettingsItem baseItem = getBaseItem(SettingsItemType.ONLINE_ROUTING_ENGINES, OnlineRoutingSettingsItem.class, settingsItems);
			result.add(new OnlineRoutingSettingsItem(app, baseItem, onlineRoutingEngines));
		}
		if (!itineraryGroups.isEmpty()) {
			ItinerarySettingsItem baseItem = getBaseItem(SettingsItemType.ITINERARY_GROUPS, ItinerarySettingsItem.class, settingsItems);
			result.add(new ItinerarySettingsItem(app, baseItem, itineraryGroups));
		}
		return result;
	}

	@Nullable
	private ProfileSettingsItem getBaseProfileSettingsItem(ApplicationModeBean modeBean, List<SettingsItem> settingsItems) {
		for (SettingsItem settingsItem : settingsItems) {
			if (settingsItem.getType() == SettingsItemType.PROFILE) {
				ProfileSettingsItem profileItem = (ProfileSettingsItem) settingsItem;
				ApplicationModeBean bean = profileItem.getModeBean();
				if (Algorithms.objectEquals(bean.stringKey, modeBean.stringKey) && Algorithms.objectEquals(bean.userProfileName, modeBean.userProfileName)) {
					return profileItem;
				}
			}
		}
		return null;
	}

	@Nullable
	private <T> T getBaseItem(SettingsItemType settingsItemType, Class<T> clazz, List<SettingsItem> settingsItems) {
		for (SettingsItem settingsItem : settingsItems) {
			if (settingsItem.getType() == settingsItemType && clazz.isInstance(settingsItem)) {
				return clazz.cast(settingsItem);
			}
		}
		return null;
	}

	public static Map<ExportSettingsCategory, SettingsCategoryItems> getSettingsToOperateByCategory(List<SettingsItem> items, boolean importComplete, boolean addEmptyItems) {
		Map<ExportType, List<?>> settingsToOperate = getSettingsToOperate(items, importComplete, addEmptyItems);
		return getSettingsToOperateByCategory(settingsToOperate, addEmptyItems);
	}

	public static Map<ExportSettingsCategory, SettingsCategoryItems> getSettingsToOperateByCategory(Map<ExportType, List<?>> settingsToOperate, boolean addEmptyItems) {
		Map<ExportType, List<?>> settingsItems = new LinkedHashMap<>();
		Map<ExportType, List<?>> myPlacesItems = new LinkedHashMap<>();
		Map<ExportType, List<?>> resourcesItems = new LinkedHashMap<>();

		for (Map.Entry<ExportType, List<?>> entry : settingsToOperate.entrySet()) {
			ExportType type = entry.getKey();
			if (type.isSettingsCategory()) {
				settingsItems.put(type, entry.getValue());
			} else if (type.isMyPlacesCategory()) {
				myPlacesItems.put(type, entry.getValue());
			} else if (type.isResourcesCategory()) {
				resourcesItems.put(type, entry.getValue());
			}
		}
		Map<ExportSettingsCategory, SettingsCategoryItems> exportMap = new LinkedHashMap<>();
		if (!settingsItems.isEmpty() || addEmptyItems) {
			exportMap.put(ExportSettingsCategory.SETTINGS, new SettingsCategoryItems(settingsItems));
		}
		if (!myPlacesItems.isEmpty() || addEmptyItems) {
			exportMap.put(ExportSettingsCategory.MY_PLACES, new SettingsCategoryItems(myPlacesItems));
		}
		if (!resourcesItems.isEmpty() || addEmptyItems) {
			exportMap.put(ExportSettingsCategory.RESOURCES, new SettingsCategoryItems(resourcesItems));
		}

		return exportMap;
	}

	public static Map<ExportType, List<?>> getSettingsToOperate(List<SettingsItem> settingsItems, boolean importComplete, boolean addEmptyItems) {
		Map<ExportType, List<?>> settingsToOperate = new EnumMap<>(ExportType.class);
		List<ApplicationModeBean> profiles = new ArrayList<>();
		List<QuickAction> quickActions = new ArrayList<>();
		List<PoiUIFilter> poiUIFilters = new ArrayList<>();
		List<ITileSource> tileSourceTemplates = new ArrayList<>();
		List<File> routingFilesList = new ArrayList<>();
		List<File> renderFilesList = new ArrayList<>();
		List<File> ttsVoiceFilesList = new ArrayList<>();
		List<File> voiceFilesList = new ArrayList<>();
		List<FileSettingsItem> mapFilesList = new ArrayList<>();
		List<FileSettingsItem> wikiFilesList = new ArrayList<>();
		List<FileSettingsItem> terrainFilesList = new ArrayList<>();
		List<FileSettingsItem> roadsOnlyFilesList = new ArrayList<>();
		List<FileSettingsItem> nauticalFilesList = new ArrayList<>();
		List<FileSettingsItem> tracksFilesList = new ArrayList<>();
		List<FileSettingsItem> favouritesBackupFilesList = new ArrayList<>();
		List<FileSettingsItem> multimediaFilesList = new ArrayList<>();
		List<AvoidRoadInfo> avoidRoads = new ArrayList<>();
		List<GlobalSettingsItem> globalSettingsItems = new ArrayList<>();
		List<OsmNotesPoint> notesPointList = new ArrayList<>();
		List<OpenstreetmapPoint> editsPointList = new ArrayList<>();
		List<FavoriteGroup> favoriteGroups = new ArrayList<>();
		List<MapMarkersGroup> markersGroups = new ArrayList<>();
		List<HistoryEntry> historySearchEntries = new ArrayList<>();
		List<HistoryEntry> historyNavigationEntries = new ArrayList<>();
		List<OnlineRoutingEngine> onlineRoutingEngines = new ArrayList<>();
		List<MapMarkersGroup> itineraryGroups = new ArrayList<>();

		for (SettingsItem item : settingsItems) {
			switch (item.getType()) {
				case PROFILE:
					profiles.add(((ProfileSettingsItem) item).getModeBean());
					break;
				case FILE:
					FileSettingsItem fileItem = (FileSettingsItem) item;
					if (fileItem.getSubtype() == FileSubtype.RENDERING_STYLE) {
						renderFilesList.add(fileItem.getFile());
					} else if (fileItem.getSubtype() == FileSubtype.ROUTING_CONFIG) {
						routingFilesList.add(fileItem.getFile());
					} else if (fileItem.getSubtype() == FileSubtype.MULTIMEDIA_NOTES) {
						multimediaFilesList.add(fileItem);
					} else if (fileItem.getSubtype() == FileSubtype.GPX) {
						tracksFilesList.add(fileItem);
					} else if (fileItem.getSubtype() == FileSubtype.FAVORITES_BACKUP) {
						favouritesBackupFilesList.add(fileItem);
					} else if (fileItem.getSubtype() == FileSubtype.OBF_MAP) {
						mapFilesList.add(fileItem);
					} else if (fileItem.getSubtype() == FileSubtype.WIKI_MAP || fileItem.getSubtype() == FileSubtype.TRAVEL) {
						wikiFilesList.add(fileItem);
					} else if (fileItem.getSubtype() == FileSubtype.SRTM_MAP || fileItem.getSubtype() == FileSubtype.TERRAIN_DATA) {
						terrainFilesList.add(fileItem);
					} else if (fileItem.getSubtype() == FileSubtype.ROAD_MAP) {
						roadsOnlyFilesList.add(fileItem);
					} else if (fileItem.getSubtype() == FileSubtype.NAUTICAL_DEPTH) {
						nauticalFilesList.add(fileItem);
					} else if (fileItem.getSubtype() == FileSubtype.TTS_VOICE) {
						ttsVoiceFilesList.add(fileItem.getFile());
					} else if (fileItem.getSubtype() == FileSubtype.VOICE) {
						voiceFilesList.add(fileItem.getFile());
					}
					break;
				case QUICK_ACTIONS:
					QuickActionsSettingsItem quickActionsItem = (QuickActionsSettingsItem) item;
					if (importComplete) {
						quickActions.addAll(quickActionsItem.getAppliedItems());
					} else {
						quickActions.addAll(quickActionsItem.getItems());
					}
					break;
				case POI_UI_FILTERS:
					PoiUiFiltersSettingsItem poiUiFilterItem = (PoiUiFiltersSettingsItem) item;
					if (importComplete) {
						poiUIFilters.addAll(poiUiFilterItem.getAppliedItems());
					} else {
						poiUIFilters.addAll(poiUiFilterItem.getItems());
					}
					break;
				case MAP_SOURCES:
					MapSourcesSettingsItem mapSourcesItem = (MapSourcesSettingsItem) item;
					if (importComplete) {
						tileSourceTemplates.addAll(mapSourcesItem.getAppliedItems());
					} else {
						tileSourceTemplates.addAll(mapSourcesItem.getItems());
					}
					break;
				case AVOID_ROADS:
					AvoidRoadsSettingsItem avoidRoadsItem = (AvoidRoadsSettingsItem) item;
					if (importComplete) {
						avoidRoads.addAll(avoidRoadsItem.getAppliedItems());
					} else {
						avoidRoads.addAll(avoidRoadsItem.getItems());
					}
					break;
				case GLOBAL:
					globalSettingsItems.add((GlobalSettingsItem) item);
					break;
				case OSM_NOTES:
					OsmNotesSettingsItem osmNotesSettingsItem = (OsmNotesSettingsItem) item;
					if (importComplete) {
						notesPointList.addAll(osmNotesSettingsItem.getAppliedItems());
					} else {
						notesPointList.addAll(osmNotesSettingsItem.getItems());
					}
					break;
				case OSM_EDITS:
					OsmEditsSettingsItem osmEditsSettingsItem = (OsmEditsSettingsItem) item;
					if (importComplete) {
						editsPointList.addAll(osmEditsSettingsItem.getAppliedItems());
					} else {
						editsPointList.addAll(osmEditsSettingsItem.getItems());
					}
					break;
				case FAVOURITES:
					FavoritesSettingsItem favoritesSettingsItem = (FavoritesSettingsItem) item;
					favoriteGroups.addAll(favoritesSettingsItem.getItems());
					break;
				case ACTIVE_MARKERS:
					MarkersSettingsItem markersSettingsItem = (MarkersSettingsItem) item;
					markersGroups.add(markersSettingsItem.getMarkersGroup());
					break;
				case HISTORY_MARKERS:
					HistoryMarkersSettingsItem historyMarkersSettingsItem = (HistoryMarkersSettingsItem) item;
					markersGroups.add(historyMarkersSettingsItem.getMarkersGroup());
					break;
				case SEARCH_HISTORY:
					SearchHistorySettingsItem searchHistorySettingsItem = (SearchHistorySettingsItem) item;
					historySearchEntries.addAll(searchHistorySettingsItem.getItems());
					break;
				case NAVIGATION_HISTORY:
					NavigationHistorySettingsItem navigationHistorySettingsItem = (NavigationHistorySettingsItem) item;
					historyNavigationEntries.addAll(navigationHistorySettingsItem.getItems());
					break;
				case GPX:
					tracksFilesList.add((GpxSettingsItem) item);
					break;
				case ONLINE_ROUTING_ENGINES:
					OnlineRoutingSettingsItem onlineRoutingSettingsItem = (OnlineRoutingSettingsItem) item;
					onlineRoutingEngines.addAll(onlineRoutingSettingsItem.getItems());
					break;
				case ITINERARY_GROUPS:
					ItinerarySettingsItem itinerarySettingsItem = (ItinerarySettingsItem) item;
					itineraryGroups.addAll(itinerarySettingsItem.getItems());
					break;
				default:
					break;
			}
		}
		for (SettingsItem item : settingsItems) {
			switch (item.getType()) {
				case PROFILE:
					if (!profiles.isEmpty() || addEmptyItems) {
						settingsToOperate.put(ExportType.PROFILE, profiles);
					}
					break;
				case FILE:
					FileSettingsItem fileItem = (FileSettingsItem) item;
					if (fileItem.getSubtype() == FileSubtype.RENDERING_STYLE) {
						if (!renderFilesList.isEmpty() || addEmptyItems) {
							settingsToOperate.put(ExportType.CUSTOM_RENDER_STYLE, renderFilesList);
						}
					} else if (fileItem.getSubtype() == FileSubtype.ROUTING_CONFIG) {
						if (!routingFilesList.isEmpty() || addEmptyItems) {
							settingsToOperate.put(ExportType.CUSTOM_ROUTING, routingFilesList);
						}
					} else if (fileItem.getSubtype() == FileSubtype.MULTIMEDIA_NOTES) {
						if (!multimediaFilesList.isEmpty() || addEmptyItems) {
							settingsToOperate.put(ExportType.MULTIMEDIA_NOTES, multimediaFilesList);
						}
					} else if (fileItem.getSubtype() == FileSubtype.GPX) {
						if (!tracksFilesList.isEmpty() || addEmptyItems) {
							settingsToOperate.put(ExportType.TRACKS, tracksFilesList);
						}
					} else if (fileItem.getSubtype() == FileSubtype.FAVORITES_BACKUP) {
						if (!favouritesBackupFilesList.isEmpty() || addEmptyItems) {
							settingsToOperate.put(ExportType.FAVORITES_BACKUP, favouritesBackupFilesList);
						}
					} else if (fileItem.getSubtype() == FileSubtype.OBF_MAP) {
						if (!mapFilesList.isEmpty() || addEmptyItems) {
							settingsToOperate.put(ExportType.STANDARD_MAPS, mapFilesList);
						}
					} else if (fileItem.getSubtype() == FileSubtype.WIKI_MAP || fileItem.getSubtype() == FileSubtype.TRAVEL) {
						if (!wikiFilesList.isEmpty() || addEmptyItems) {
							settingsToOperate.put(ExportType.WIKI_AND_TRAVEL, wikiFilesList);
						}
					} else if (fileItem.getSubtype() == FileSubtype.SRTM_MAP || fileItem.getSubtype() == FileSubtype.TERRAIN_DATA) {
						if (!terrainFilesList.isEmpty() || addEmptyItems) {
							settingsToOperate.put(ExportType.TERRAIN_DATA, terrainFilesList);
						}
					} else if (fileItem.getSubtype() == FileSubtype.ROAD_MAP) {
						if (!roadsOnlyFilesList.isEmpty() || addEmptyItems) {
							settingsToOperate.put(ExportType.ROAD_MAPS, roadsOnlyFilesList);
						}
					} else if (fileItem.getSubtype() == FileSubtype.NAUTICAL_DEPTH) {
						if (!nauticalFilesList.isEmpty() || addEmptyItems) {
							settingsToOperate.put(ExportType.DEPTH_DATA, nauticalFilesList);
						}
					} else if (fileItem.getSubtype() == FileSubtype.TTS_VOICE) {
						if (!ttsVoiceFilesList.isEmpty() || addEmptyItems) {
							settingsToOperate.put(ExportType.TTS_VOICE, ttsVoiceFilesList);
						}
					} else if (fileItem.getSubtype() == FileSubtype.VOICE) {
						if (!voiceFilesList.isEmpty() || addEmptyItems) {
							settingsToOperate.put(ExportType.VOICE, voiceFilesList);
						}
					}
					break;
				case QUICK_ACTIONS:
					if (!quickActions.isEmpty() || addEmptyItems) {
						settingsToOperate.put(ExportType.QUICK_ACTIONS, quickActions);
					}
					break;
				case POI_UI_FILTERS:
					if (!poiUIFilters.isEmpty() || addEmptyItems) {
						settingsToOperate.put(ExportType.POI_TYPES, poiUIFilters);
					}
					break;
				case MAP_SOURCES:
					if (!tileSourceTemplates.isEmpty() || addEmptyItems) {
						settingsToOperate.put(ExportType.MAP_SOURCES, tileSourceTemplates);
					}
					break;
				case AVOID_ROADS:
					if (!avoidRoads.isEmpty() || addEmptyItems) {
						settingsToOperate.put(ExportType.AVOID_ROADS, avoidRoads);
					}
					break;
				case GLOBAL:
					if (!globalSettingsItems.isEmpty() || addEmptyItems) {
						settingsToOperate.put(ExportType.GLOBAL, globalSettingsItems);
					}
					break;
				case OSM_NOTES:
					if (!notesPointList.isEmpty() || addEmptyItems) {
						settingsToOperate.put(ExportType.OSM_NOTES, notesPointList);
					}
					break;
				case OSM_EDITS:
					if (!editsPointList.isEmpty() || addEmptyItems) {
						settingsToOperate.put(ExportType.OSM_EDITS, editsPointList);
					}
					break;
				case FAVOURITES:
					if (!favoriteGroups.isEmpty() || addEmptyItems) {
						settingsToOperate.put(ExportType.FAVORITES, favoriteGroups);
					}
					break;
				case ACTIVE_MARKERS:
				case HISTORY_MARKERS:
					if (!markersGroups.isEmpty() || addEmptyItems) {
						settingsToOperate.put(ExportType.ACTIVE_MARKERS, markersGroups);
					}
					break;
				case SEARCH_HISTORY:
					if (!historySearchEntries.isEmpty() || addEmptyItems) {
						settingsToOperate.put(ExportType.SEARCH_HISTORY, historySearchEntries);
					}
					break;
				case NAVIGATION_HISTORY:
					if (!historyNavigationEntries.isEmpty() || addEmptyItems) {
						settingsToOperate.put(ExportType.NAVIGATION_HISTORY, historyNavigationEntries);
					}
					break;
				case GPX:
					if (!tracksFilesList.isEmpty() || addEmptyItems) {
						settingsToOperate.put(ExportType.TRACKS, tracksFilesList);
					}
					break;
				case ONLINE_ROUTING_ENGINES:
					if (!onlineRoutingEngines.isEmpty() || addEmptyItems) {
						settingsToOperate.put(ExportType.ONLINE_ROUTING_ENGINES, onlineRoutingEngines);
					}
					break;
				case ITINERARY_GROUPS:
					if (!itineraryGroups.isEmpty() || addEmptyItems) {
						settingsToOperate.put(ExportType.ITINERARY_GROUPS, itineraryGroups);
					}
					break;
				default:
					break;
			}
		}
		return settingsToOperate;
	}

	private void sortLocalFiles(List<File> files) {
		Collator collator = OsmAndCollator.primaryCollator();
		Collections.sort(files, new Comparator<File>() {
			@Override
			public int compare(File lhs, File rhs) {
				return collator.compare(getNameToDisplay(lhs), getNameToDisplay(rhs));
			}

			private String getNameToDisplay(File item) {
				return FileNameTranslationHelper.getFileNameWithRegion(app, item.getName());
			}
		});
	}
}
