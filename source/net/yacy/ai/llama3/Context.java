/**
 *  Context
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

package net.yacy.ai.llama3;


public final class Context {
    
    public final String prompt;
    public final String systemPrompt;
    public final float temp;
    public final float topp;
    public final long seed;
    public final int maxTokens;

    public static final int DEFAULT_MAX_TOKENS = 512;

    /**
     * Create a context.
     * 
     * @param prompt the prompt to use for the model 
     * @param systemPrompt a system prompt to use for the model
     * @param temp temperature for sampling, must be >= 0; 0 means greedy sampling (deterministic)
     * @param topp if 0 <= topp <= 1, use top-p (nucleus) sampling; otherwise, use categorical sampling
     * @param seed random seed for sampling; use 0 for a random seed
     * @param maxTokens maximum number of tokens to generate; use 0 for no limit (default is 512)
     */
    public Context(String prompt, String systemPrompt, 
                  float temp, float topp, long seed, int maxTokens) {
        assert(0 <= temp): "temperature must be positive";
        assert(0 <= topp && topp <= 1): "top-p must be between 0 and 1";

        this.prompt = prompt;
        this.systemPrompt = systemPrompt;
        this.temp = temp;
        this.topp = topp;
        this.seed = seed;
        this.maxTokens = maxTokens;
    }
}