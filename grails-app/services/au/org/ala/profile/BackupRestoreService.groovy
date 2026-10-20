package au.org.ala.profile

import org.springframework.scheduling.annotation.Async

class BackupRestoreService {

    def grailsApplication
    /**
     * Call bash script to create a backup for the one or more collections
     *
     * @param backupOpusUuids: list of uuids for eg: ["5547200f-94ee-4725-b5d1-08daeeb33ad4", "bf6bf7f5-56d4-438b-84ec-045870115200"]
     * @param outputDir: backup folder where the backup is to be created for eg: "/data/profile-service/backup/db"
     * @param backupName: backup file name
     */
    @Async
    void backupCollections(def backupOpusUuids, String backupDir, String backupName) {
        String currentDB = getCurrentDB()
        String userName = getDBUser()
        String password = getDBPassword()
        executeOnShell("${getScriptPath()}/backup.sh -b ${currentDB} ${backupDir} ${backupName} ${backupOpusUuids.toString()} ${userName} ${password}")
    }

    /**
     * Call bash script to restore one or more backup files
     *
     * @param backupDir: backup folder where the backup is to be restored from for eg: "/data/profile-service/backup/db"
     * @param backupNames: list of backupDirs for eg: ["test", "masterlist"]
     * @param restoreDB: DB name to be restored to
     */
    @Async
    void restoreCollections(String backupDir, def backupNames, String restoreToDB) {
        String currentDB = getCurrentDB()
        String userName = getDBUser()
        String password = getDBPassword()
        executeOnShell("${getScriptPath()}/backup.sh -r ${currentDB} ${backupDir} ${backupNames.toString()} ${restoreToDB} ${userName} ${password}")
    }

    private String getCurrentDB() {
        return grailsApplication.config.getProperty('grails.mongodb.databaseName')?:"profiles"
    }

    private String getDBUser() {
        return grailsApplication.config.getProperty('grails.mongodb.username')?:""
    }

    private String getDBPassword() {
        return grailsApplication.config.getProperty('grails.mongodb.password')?:""
    }

    private String getScriptPath() {
        return grailsApplication.config.getProperty('backupScript.path')?:"/data/profile-service/config"
    }

    private executeOnShell(String command) {
        Process process = command.execute()
        def out = new StringBuffer()
        def err = new StringBuffer()
        process.consumeProcessOutput( out, err )
        process.waitForProcessOutput(out, err)
        log.info(out?.toString())
        log.info(err?.toString())

        return process.exitValue()
    }

}
