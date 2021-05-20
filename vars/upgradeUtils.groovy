// Helper functions for upgrades

def previous_version(String sat_version) {
    // Calculates and Returns the previous major release from sat_verion
    def (major, minor) = sat_version.tokenize('.')
    def last_version = [major, minor.toInteger()-1].join('.')
    return last_version
}
