(ns gnowdb.spec.files
  (:gen-class)
  (:require [gnowdb.neo4j.gneo :as gneo]
  			[digest :as digest]
  			[clojure.java.shell :refer [sh]]
  			[gnowdb.specs.workspaces :as workspaces]
  			[pantomime.mime :refer [mime-type-of]]
  			[pantomime.extract :as extract]
  )
)

(defn- createFileClass
	[] 
	(gneo/createClass :className "GDB_File" :classType "NODE" :isAbstract? false :subClassOf ["GDB_Node"] :properties {} :execute? true)
	(gneo/createAttributeType :_name "GDB_Path" :_datatype "java.lang.String" :subTypeOf [] :execute? true)
	(gneo/createAttributeType :_name "GDB_Extension" :_datatype "java.lang.String" :subTypeOf [] :execute? true)
	(gneo/createAttributeType :_name "GDB_Size" :_datatype "java.lang.Double" :subTypeOf [] :execute? true)
	(gneo/createAttributeType :_name "GDB_MimeType" :_datatype "java.lang.String" :subTypeOf [] :execute? true)
	(gneo/createAttributeType :_name "GDB_FileID" :_datatype "java.lang.String" :subTypeOf [] :execute? true)
	(gneo/addClassAT :_atname "GDB_Path" :className "GDB_File" :execute? true)
	(gneo/addClassAT :_atname "GDB_Extension" :className "GDB_File" :execute? true)
	(gneo/addClassAT :_atname "GDB_Size" :className "GDB_File" :execute? true)	
	(gneo/addClassAT :_atname "GDB_FileID" :className "GDB_File" :execute? true)
	(gneo/addClassAT :_atname "GDB_MimeType" :className "GDB_File" :execute? true)
)

(defn mkdir
	"filePath e.g. 1/2/3"
	[filePath]
	(sh "mkdir -p" filePath)
)

(defn copyFile
	[srcPath destPath]
	(sh "cp" srcPath destPath)
)

(defn copyFileToDir
	[srcPath filePath]
	(mkdir filePath)
	(copyFile srcPath filePath)
	)

(defn derivePath
	[GDB_MD5]
	(let [last3DigitString (subs GDB_MD5 (- (count GDB_MD5) 3)) 
		  filePath (str (subs last3DigitString 2 3) "/" (subs last3DigitString 1 2) "/" (subs last3DigitString 0 1))]
		  filePath
	)
)

(defn getMetaData 
	[filePath]
	(extract/parse filePath)
)

(defn createFileInstance 
	[& {:keys[:fileName :filePath :author :memberOfWorkspace]} :or {:author "ADMIN" :memberOfWorkspace []}]
	(gneo/createNodeClassInstances :className "GDB_File" :nodeList 	[{
																		"GDB_DisplayName" fileName
																		"GDB_Extension" (subs fileName (inc (clojure.string/last-index-of fileName ".")))
																		"GDB_Size" (.length (java.io.File. filePath))
																		"GDB_Path" filePath
																		"GDB_MD5" (digest/md5 fileName)
																		"GDB_MimeType" (mime-type-of (str fileName))
																		"GDB_ModifiedAt" (.toString (new java.util.Date (.lastModified (java.io.File. filePath))))
																		"GDB_CreatedAt" (.toString (new java.util.Date))
																	}]
	)
	(gneo/createRelationClassInstances :className "GDB_CreatedBy" :relList 	[{
																				:fromClassName "GDB_File"
																				:fromPropertyMap {"GDB_DisplayName" fileName}
																				:toClassName "GDB_PersonalWorkspace"
																				:toPropertyMap {"GDB_DisplayName" author}
																				:propertyMap {}
																			}]
	)
	(gneo/createRelationClassInstances :className "GDB_lastModifiedBy" :relList [{
																					:fromClassName "GDB_File"
																					:fromPropertyMap {"GDB_DisplayName" fileName}
																					:toClassName "GDB_PersonalWorkspace"
																					:toPropertyMap {"GDB_DisplayName" author}
																					:propertyMap {}
																				}]
	)
	(gneo/createRelationClassInstances :className "GDB_MemberOfWorkspace" :relList 	[{
																							:fromClassName "GDB_File"
																							:fromPropertyMap {"GDB_DisplayName" fileName}
																							:toClassName "GDB_PersonalWorkspace"
																							:toPropertyMap {"GDB_DisplayName" author}
																							:propertyMap {}
																					}]
	)
	(if (not (empty? memberOfWorkspace))	
		(do
			(map [groupName]
				(do
					(gneo/createRelationClassInstances :className "GDB_MemberOfWorkspace" :relList 	[{
																										:fromClassName "GDB_File"
																										:fromPropertyMap {"GDB_DisplayName" fileName}
																										:toClassName "GDB_GroupWorkspace"
																										:toPropertyMap {"GDB_DisplayName" groupName}
																										:propertyMap {}
																									}]
					)
					(let [workspace (first (gneo/getNodes 
                        	:label "GDB_GroupWorkspace" 
                        	:parameters {
                            	       		"GDB_DisplayName" groupName
                                	    }
                    		))
						editingPolicy ((workspace :properties) "GDB_EditingPolicy")
						]	
						(if (= editingPolicy "Editable_Moderated")
							(
								let [admins (workspaces/getAdminList groupName)]
								(if (not (.contains admins author))
									(gneo/createRelationClassInstances 	:className "GDB_PendingReview" 	
																		:relList 	[{
																						:fromClassName "GDB_File"
																						:fromPropertyMap {"GDB_DisplayName" fileName}
																						:toClassName "GDB_GroupWorkspace"
																						:toPropertyMap {"GDB_DisplayName" groupName}
																						:propertyMap {}
																					}]
									)		
								)
							)
						)			
					)
				)
				memberOfWorkspace
			)
		)
	)
)

(defn addFile
	[& {:keys[:fileName :srcFilePath :author :memberOfWorkspace]}]
	let [
			filePath (derivePath (digest/md5 fileName))
		]
		(createFileInstance :fileName fileName :filePath filePath :author author :memberOfWorkspace memberOfWorkspace)
		(copyFileToDir srcFilePath filePath)
)

(defn init 
	[]
	(createFileClass)
)

