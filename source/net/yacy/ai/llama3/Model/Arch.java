/**
 *  Arch
 *  Copyright 2025 by Michael Peter Christen
 *  First released 25.05.2025 at https://yacy.net
 *  
 **  This class was not part of the original llama3 implementation,
 **  but added later by the author to support different architectures.
 **  It therefore does not inherit the llama3 copyright.
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

package net.yacy.ai.llama3.Model;


// architecture of the model, see https://github.com/ggml-org/llama.cpp/blob/master/src/llama-arch.cpp
public enum Arch {
    LLM_ARCH_LLAMA, LLM_ARCH_QWEN2, LLM_ARCH_QWEN3, LLM_ARCH_OLMO2
}
