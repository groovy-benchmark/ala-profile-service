package au.org.ala.profile

import au.org.ala.profile.util.Utils

/**
 * Embedded entity to encapsulate the configuration items that control the maps on the UI
 */
class MapConfig {
    String mapAttribution // e.g. AVH (CHAH)
    String mapPointColour
    Float mapDefaultLatitude
    Float mapDefaultLongitude
    Integer mapZoom
    String mapBaseLayer
    String biocacheUrl    // e.g.  http://avh.ala.org.au/
    String biocacheName    ///e.g. Australian Virtual Herbarium
    Integer maxZoom
    Integer maxAutoZoom
    boolean autoZoom = false
    boolean allowSnapshots = false

    static constraints = {
        mapAttribution nullable: true
        mapPointColour nullable: true
        mapDefaultLatitude nullable: true
        mapDefaultLongitude nullable: true
        mapZoom nullable: true
        mapBaseLayer nullable: true
        biocacheUrl nullable: true
        biocacheName nullable: true
        maxZoom nullable: true
        maxAutoZoom nullable: true
    }

    static mapping = {
        mapDefaultLatitude defaultValue: Utils.DEFAULT_MAP_LATITUDE
        mapDefaultLongitude defaultValue: Utils.DEFAULT_MAP_LONGITUDE
        mapPointColour defaultValue: Utils.DEFAULT_MAP_POINT_COLOUR
        mapZoom defaultValue: Utils.DEFAULT_MAP_ZOOM
        maxZoom defaultValue: Utils.DEFAULT_MAP_MAX_ZOOM
        maxAutoZoom defaultValue: Utils.DEFAULT_MAP_MAX_AUTO_ZOOM
        mapBaseLayer defaultValue: Utils.DEFAULT_MAP_BASE_LAYER
        autoZoom defaultValue: false
        allowSnapshots defaultValue: false
    }
}
