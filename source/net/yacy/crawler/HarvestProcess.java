/**
 *  HarvestProcess
 *  SPDX-FileCopyrightText: 2012 Michael Peter Christen <mc@yacy.net)> 
 *  SPDX-License-Identifier: GPL-2.0-or-later
 *  First released 06.12.2012 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.crawler;

public enum HarvestProcess {

    DELEGATED, ERRORS, CRAWLER, WORKER, LOADED;
    
}
