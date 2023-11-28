package com.dzikoysk.sqiffy.e2e

import com.dzikoysk.sqiffy.definition.FunctionDefinition
import com.dzikoysk.sqiffy.definition.FunctionVersion

@FunctionDefinition(
    name = "gen_random_bytes",
    versions = [
        FunctionVersion(
            version = UserAndGuildScenarioVersions.V_1_0_0,
            parameters = ["int"],
            returnType = "bytea",
            body = """
                '${'$'}libdir/pgcrypto',
                'pg_random_bytes' language c strict;
            """
        )
    ]
)
const val GenRandomBytesFunction = "gen_random_bytes"

@FunctionDefinition(
    name = "random_string",
    versions = [
        FunctionVersion(
            version = UserAndGuildScenarioVersions.V_1_0_0,
            parameters = ["len int"],
            returnType = "text",
            body = """
                ${'$'}${'$'}
                declare
                    chars  text[] = '{0,1,2,3,4,5,6,7,8,9,A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z,a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z}';
                    result text   = '';
                    i      int    = 0;
                    rand   bytea;
                begin
                    -- generate secure random bytes and convert them to a string of chars.
                    rand = gen_random_bytes(${'$'}1);
                    for i in 0..len - 1
                        loop
                            -- rand indexing is zero-based, chars is 1-based.
                            result = result || chars[1 + (get_byte(rand, i) % array_length(chars, 1))];
                        end loop;
                    return result;
                end;
                ${'$'}${'$'} language plpgsql;
            """
        )
    ]
)
const val RandomStringFunction = "random_string"

