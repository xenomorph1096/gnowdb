(ns gnowdb.spec.files
  (:gen-class)
  (:require [gnowdb.neo4j.gneo :as gneo]
            [digest :as digest]
            [clojure.java.shell :refer [sh]]
            [gnowdb.spec.workspaces :as workspaces]
            [pantomime.mime :refer [mime-type-of]]
            [pantomime.extract :as extract]
            [clojure.java.io :as io]
            )
  )

(defn getDataDirectory
  [details]
  (def ^{:private true} dataDir 
    (details :data-directory)
    )
  )

(defn getDataStorageLevels
  [details]
  (def ^{:private true} dataStorageLevels 
    (details :data-storage-levels)
    )
  )

(defn- createFileClass
  "Creates a file class with all attributes."
  [] 
  (gneo/createClass :className "GDB_File" :classType "NODE" :isAbstract? false :subClassOf ["GDB_Node"] :properties {} :execute? true)
  (gneo/createAttributeType :_name "GDB_Path" :_datatype "java.lang.String" :subTypeOf [] :execute? true)
  (gneo/createAttributeType :_name "GDB_Extension" :_datatype "java.lang.String" :subTypeOf [] :execute? true)
  (gneo/createAttributeType :_name "GDB_Size" :_datatype "java.lang.Long" :subTypeOf [] :execute? true)
  (gneo/createAttributeType :_name "GDB_MimeType" :_datatype "java.lang.String" :subTypeOf [] :execute? true)
  (gneo/createAttributeType :_name "GDB_MD5" :_datatype "java.lang.String" :subTypeOf [] :execute? true)
                                        ;(gneo/createAttributeType :_name "GDB_FileID" :_datatype "java.lang.String" :subTypeOf [] :execute? true)
  (gneo/addClassAT :_atname "GDB_Path" :className "GDB_File" :execute? true)
  (gneo/addClassAT :_atname "GDB_Extension" :className "GDB_File" :execute? true)
  (gneo/addClassAT :_atname "GDB_Size" :className "GDB_File" :execute? true)	
  (gneo/addClassAT :_atname "GDB_MD5" :className "GDB_File" :execute? true)
  (gneo/addClassAT :_atname "GDB_MimeType" :className "GDB_File" :execute? true)
                                        ;(gneo/addClassAT :_atname "GDB_FileID" :className "GDB_File" :execute? true)
  )

(defn- derivePath
  "Derives the file path(e.g.1/2/3) from the md5 hash where the file is to be stored inside the data directory"
  [& {:keys [:GDB_MD5]}]
  (let [
        endSubString (subs GDB_MD5 (- (count GDB_MD5) dataStorageLevels)) 
        reversedString (clojure.string/reverse endSubString)
        splitVec (clojure.string/split reversedString #"")
        ]
    (clojure.string/join "/" splitVec)
    )
  )

(defn- copyFileToDataDir
  "Copies a file from the source folder to data folder."
  [& {:keys [:filePath :srcPath]}]
  (sh "mkdir" "-p" filePath)
  (sh "cp" srcPath filePath)
  )

(defn- removeFilefromDataDir
  "Removes a file from the data directory."
  [& {:keys [:GDB_MD5 :fileName]}]
  (sh "rm" (str (derivePath :GDB_MD5 GDB_MD5) "/" fileName))
  )

(defn- generateMD5 
  "Generates MD5 hash using the source path of the file."
  [& {:keys [:filePath]}]
  (digest/md5 ((extract/parse filePath) :text))
  )

(defn- deriveFileName
  "Derives the filename from the given path of the file."
  [& {:keys [:filePath]}]
  (subs filePath (inc (clojure.string/last-index-of filePath "/")))
  )

(defn- fileExists
  "Returns true if the given workspace contains the file else false"
  [& {:keys [:GDB_MD5 :workspaceName :workspaceClass]}]
  (workspaces/resourceExists :resourceIDMap {"GDB_MD5" GDB_MD5} :resourceClass "GDB_File" :workspaceClass workspaceClass :workspaceName workspaceName)
  )

(defn- createFileInstance 
  ":fileSrcPath should include the filename as well e.g. src/gnowdb/core.clj.
	 :author is the name of the user uploading the file .
	 :memberOfWorkspace is a vector containing names of groupworkspaces which will
	  contain the file.
	  if PersonalWorkspace of author already contains the file then the file instance is not created.
	  if GroupWorkspace already contains the file then the file is not published to that group."
  [& {:keys[:fileSrcPath :author :memberOfWorkspace] :or {:author "ADMIN" :memberOfWorkspace []}}]
  (let [	fileName (deriveFileName :filePath fileSrcPath) 
        GDB_MD5 (generateMD5 :filePath fileSrcPath)
        filePath (derivePath :GDB_MD5 GDB_MD5)]
    (if (not (fileExists :GDB_MD5 GDB_MD5 :workspaceName author :workspaceClass "GDB_PersonalWorkspace"))
      (do
        (gneo/createNodeClassInstances :className "GDB_File" :nodeList 	[{
                                                                          "GDB_DisplayName" fileName 
                                                                          "GDB_Extension" (subs fileName (inc (clojure.string/last-index-of fileName ".")))
                                                                          "GDB_Size" (.length (java.io.File. fileSrcPath))
                                                                          "GDB_Path" filePath
                                                                          "GDB_MD5" GDB_MD5
                                        ;	"GDB_FileID" 
                                                                          "GDB_MimeType" (mime-type-of (str fileName))
                                                                          "GDB_ModifiedAt" (.toString (new java.util.Date (.lastModified (java.io.File. filePath))))
                                                                          "GDB_CreatedAt" (.toString (new java.util.Date))
                                                                          }]
                                       )
        (gneo/createRelationClassInstances :className "GDB_CreatedBy" :relList 	[{
                                                                                  :fromClassName "GDB_File"
                                                                                  :fromPropertyMap {"GDB_MD5" GDB_MD5}
                                                                                  :toClassName "GDB_PersonalWorkspace"
                                                                                  :toPropertyMap {"GDB_DisplayName" author}
                                                                                  :propertyMap {}
                                                                                  }]
                                           )
        (gneo/createRelationClassInstances :className "GDB_LastModifiedBy" :relList [{
                                                                                      :fromClassName "GDB_File"
                                                                                      :fromPropertyMap {"GDB_MD5" GDB_MD5}
                                                                                      :toClassName "GDB_PersonalWorkspace"
                                                                                      :toPropertyMap {"GDB_DisplayName" author}
                                                                                      :propertyMap {}
                                                                                      }]
                                           )
        (workspaces/publishToPersonalWorkspace :username author :resourceIDMap {"GDB_MD5" GDB_MD5} :resourceClass "GDB_File")
        (if (not (empty? memberOfWorkspace))
          (
           map (fn [groupName]
                 (if (not (fileExists :GDB_MD5 GDB_MD5 :workspaceName groupName :workspaceClass "GDB_GroupWorkspace"))
                   (workspaces/publishToGroup :username author :groupName groupName :resourceIDMap {"GDB_MD5" GDB_MD5} :resourceClass "GDB_File")
                   )
                 )
           memberOfWorkspace
           )
          )
        )
      (println "Personal Workspace of author already contains the file!")
      )		
    )
  )

(defn deleteFileFromGroupWorkspace
  "Deletes a file from given group workspace
		:username id the name of the user deleting the file from the group workspaceName
		:groupName is the name of the group from which the file is to be removed.
		:GDB_MD5 is the md5 hash of the file."
  [& {:keys [:username :groupName :GDB_MD5]}]
  (
   workspaces/deleteFromGroup 	:username username 
   :groupName groupName 
   :resourceIDMap {"GDB_MD5" GDB_MD5} 
   :resourceClass "GDB_File"
   )
  )

(defn deleteFileFromPersonalWorkspace
  "Deletes a file from given personal workspace
		:username id the name of the user who want's the file to be deleted from his/her personal workspace. 
		:GDB_MD5 is the md5 hash of the file."
  [& {:keys [:username :GDB_MD5]}]
  (
   workspaces/deleteFromPersonalWorkspace 	:username username 
   :resourceClass "GDB_File" 
   :resourceIDMap {"GDB_MD5" GDB_MD5}
   )
  )

(defn- deleteFileInstance
  "Deletes a file instance"
  [& {:keys[:GDB_MD5]}]
  (
   gneo/deleteDetachNodes 	:labels ["GDB_File"] 
   :parameters {"GDB_MD5" GDB_MD5}
   :execute? true
   )
  )

(defn restoreFile
  "Restores a file by moving from TRASH to the given workspace.
	Two sets of input possible:-
		:workspaceClass is GDB_GroupWorkspace.
	    :username is the name of the user who is adding the file to the given GroupWorkspace.
	    :workspaceName is the name of the workspace to which the file needs to be added.
	    :GDB_MD5 is the md5 hash of the file to be restored.

	    :workspaceClass is GDB_PersonalWorkspace.
	    :username not required.
	    :workspaceName is the name of the workspace to which the file needs to be added.
	    :GDB_MD5 is the md5 hash of the file to be restored.
    Eg:
      	1: (restoreFile :GDB_MD5 \"md5 hash\" :workspaceClass \"GDB_GroupWorkspace\" :workspaceName \"Confidential\" :username \"Hulk\")
      	2: (restoreFile :GDB_MD5 \"md5 hash\" :workspaceClass \"GDB_PersonalWorkspace\" :workspaceName \"Hulk\")"
  [& {:keys [:GDB_MD5 :workspaceClass :workspaceName :username]}]
  (workspaces/restoreResource :resourceClass "GDB_File"
                              :resourceIDMap {"GDB_MD5" GDB_MD5}
                              :workspaceName workspaceName
                              :workspaceClass workspaceClass
                              :username username
                              )
  )

(defn purgeFile
  "Removes the file from the TRASH workspace by deleting the file instance.
    	:adminName should be the name of the admin of the TRASH workspace for the file to be purged.
    	:GDB_MD5 is the md5 hash of the file to be purged.
  	Eg:
      	1: (purgeFile :adminName \"Stark\" :GDB_MD5 \"md5 hash\")"
  [& {:keys [:adminName :GDB_MD5]}]
  (workspaces/purgeTrash 	:adminName adminName 
                                :resourceClass "GDB_File"
                                :resourceIDMap {"GDB_MD5" GDB_MD5}
                                )
  )

(defn addFileToDB
  "Adds a file to the database
	 	:fileSrcPath should include the filename as well e.g. src/gnowdb/core.clj.
	 	:author is the name of the user uploading the file .
	 	:memberOfWorkspace is a vector containing names of groupworkspaces which will
	  	contain the file.
	Eg:
      	1: (addFileToDB :fileSrcPath \"src/gnowdb/core.clj\" :author \"Arrow\" :memberOfWorkspace [\"Superheroes\"])"
  [& {:keys[:fileSrcPath :author :memberOfWorkspace]:or {:memberOfWorkspace []}}]
  (createFileInstance :fileSrcPath fileSrcPath :author author :memberOfWorkspace memberOfWorkspace)
  (copyFileToDataDir :srcPath fileSrcPath :filePath (derivePath :GDB_MD5 (generateMD5 :filePath fileSrcPath)))
  )

(defn removeFileFromDB
  "Deletes a file from the database
	 :GDB_MD5 is the md5 hash of the file to be deleted."
  [& {:keys[:GDB_MD5]}]
  (let [fileName (((first (gneo/getNodes :labels ["GDB_File"] :parameters {"GDB_MD5" GDB_MD5})) :properties) "GDB_DisplayName")]
    (deleteFileInstance :GDB_MD5 GDB_MD5)
    (removeFilefromDataDir :GDB_MD5 GDB_MD5 :fileName fileName)
    )
  )

(defn init
  "Initializer function to initialize the file class with the required attribute types."
  []
  (createFileClass)
  )
