/*
 * Copyright (C) 2015 Hamburg Sud and the contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aludratest.hpalm.entity;

/*

 This file was generated by the JavaTM Architecture for XML Binding(JAXB)
 Reference Implementation, vhudson-jaxb-ri-2.1-456
 See http://www.oracle.com/technetwork/articles/javase/index-140168.html
 Any modifications to this file will be lost upon recompilation of the source schema.


 This example of an automatically generated class is an example of how one can
 generate classes from XSDs via xjc to match jaxb standards.
 XSD is a format for describing a class structure
 (note: the CLASS not an INSTANCE of the class).
 From an XSD one can generate a class java source file.
 When compiling this source file, one can "marshal" an actual object instance
 from the XML describing the object (this time we are talking about an instance,
 not a class).

 this process has many advantages, and is a form of serialization that is not
 language dependent.
 This is the recommended way of working with entities, though we do suggest you
 customize your entity class with simpler accessors.


 */

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = { "fields" })
@XmlRootElement(name = "Entity")
public class Entity {

	@XmlElement(name = "Fields", required = true)
	protected Fields fields;
	@XmlAttribute(name = "Type", required = true)
	protected String type;

	public Entity(Entity entity) {
		type = entity.getType();
		fields = new Fields(entity.getFields());
	}

	public Entity() {
	}

	/** Gets the value of the fields property.
	 * 
	 * @return possible object is {@link Fields } */
	public Fields getFields() {
		return fields;
	}

	/** Sets the value of the fields property.
	 * 
	 * @param value allowed object is {@link Fields } */
	public void setFields(Fields value) {
		this.fields = value;
	}

	/** Gets the value of the type property.
	 * 
	 * @return possible object is {@link String } */
	public String getType() {
		return type;
	}

	/** Sets the value of the type property.
	 * 
	 * @param value allowed object is {@link String } */
	public void setType(String value) {
		this.type = value;
	}

	public String getStringFieldValue(String fieldName) {
		if (fields == null) {
			return null;
		}
		for (Field f : fields.getFieldList()) {
			if (fieldName.equals(f.getName())) {
				return f.getValue() == null || f.getValue().isEmpty() ? null : f.getValue().get(0);
			}
		}
		return null;
	}

	public long getLongFieldValue(String fieldName) {
		try {
			return Long.parseLong(getStringFieldValue(fieldName));
		}
		catch (NumberFormatException e) {
			return 0;
		}
	}

	public long getId() {
		return getLongFieldValue("id");
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getType()).append(" (");

		for (Field field : getFields().getFieldList()) {
			if (sb.charAt(sb.length() - 1) != '(') {
				sb.append(", ");
			}

			sb.append(field.getName()).append(" = ");
			List<String> values = field.getValue();
			if (values == null || values.isEmpty()) {
				sb.append("null");
			}
			else if (values.size() == 1) {
				String val = values.get(0);
				if (val == null) {
					sb.append("null");
				}
				else {
					sb.append("\"").append(val).append("\"");
				}
			}
			else {
				sb.append("[");
				for (String val : values) {
					if (val == null) {
						sb.append("null");
					}
					else {
						sb.append("\"").append(val).append("\"");
					}
				}
				sb.append("]");
			}
		}

		sb.append(")");

		return sb.toString();
	}

}