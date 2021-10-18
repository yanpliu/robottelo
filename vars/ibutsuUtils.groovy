// Helper function for ibutsu sidebar

def ibutsu_sidebar() {
    log_lines = currentBuild.getRawBuild().getLog(1000)
    ibutsu_line = log_lines.find { it ==~ '.*Results can be viewed on.*(http.*ibutsu.*)'}
    if (ibutsu_line){
        ibutsu_link = ibutsu_line.substring(ibutsu_line.indexOf('http'))
        properties([
            sidebarLinks([[displayName: 'Ibutsu Test Run', iconFileName: '', urlName: ibutsu_link]])
        ])
    } else {
        println('No ibutsu run link found, no sidebar link to add')
        ibutsu_link = "missing"
    }
    return ibutsu_link
}
