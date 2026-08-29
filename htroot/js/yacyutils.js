/*    
* Copyright (C) 2026 by Michael Peter Christen
* This file is part of YaCy.
* 
* YaCy is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
* 
* YaCy is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
* 
* You should have received a copy of the GNU General Public License
* along with YaCy.  If not, see <https://www.gnu.org/licenses/>.
*/

/*
* Escape a string so it can be safely embedded into HTML (element text or attribute values).
* Modeled after ServletResource.escapeHtml in source/net/yacy/http/servlets/ServletResource.java.
* The single quote is additionally escaped so the result is safe for single-quoted attributes.
* The ampersand must be replaced first to avoid double-encoding.
*/
function escapeHtml(value){
  if (value == null) return "";
  return String(value).replace(/&/g, "&amp;").replace(/"/g, "&quot;")
    .replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/'/g, "&#39;");
}
