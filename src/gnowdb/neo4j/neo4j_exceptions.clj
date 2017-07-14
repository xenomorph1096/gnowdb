(ns gnowdb.spec.Exceptions
  (:gen-class)
)

(defn inputException
	[message]
	(throw 
		(ex-info (str "InputException:" message) {})
	)
)

(defn nameNotDefinedException
	[message]
	(throw
		(ex-info (str "NameNotDefinedException:" message) {})
	)
)

(defn nameNotProvidedException
	[message]
	(throw
		(ex-info (str "NameNotProvidedException:" message) {})
	)
)

(defn dataTypeMismatchException
	[message]
	(throw
		(ex-info (str "DataTypeMismatchException:" message) {})
	)
)

(defn dataEncodingMismatchException
	[message]
	(throw
		(ex-info (str "DataEncodingMismatchException:" message) {})
	)
)

(defn constraintFailureException
	[message]
	(throw
		(ex-info (str "ConstraintFailureException:" message) {})
	)
)

(defn keysException
	[message]
	(throw
		(ex-info (str "KeysException:" message) {})
	)
)

(defn namingException
	[message]
	(throw
		(ex-info (str "NamingException:" message) {})
	)
)

(defn relationException
	[message]
	(throw
		(ex-info (str "RelationException:" message) {})
	)	
)

(defn attributeException
	[message]
	(throw
		(ex-info (str "AttributeException:" message) {})
	)
)

(defn classException
	[message]
	(throw
		(ex-info (str "ClassException:" message) {})
	)
)

(defn throwException
	[exceptionFunctionName message]
	(eval (read-string (str "(" exceptionFunctionName " " message ")")))
)

