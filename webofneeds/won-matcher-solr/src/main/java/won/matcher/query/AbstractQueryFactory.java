/*
 * Copyright 2012  Research Studios Austria Forschungsges.m.b.H.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package won.matcher.query;

import org.apache.lucene.search.BooleanClause;

/**
 * User: gabriel
 * Date: 04.07.13
 */
public abstract class AbstractQueryFactory implements QueryFactory
{
  private BooleanClause.Occur occur;
  private float boost;

  protected AbstractQueryFactory(BooleanClause.Occur occur, final float boost)
  {
    this.occur = occur;
    this.boost = boost;
  }

  protected AbstractQueryFactory(final BooleanClause.Occur occur)
  {
    this(occur, 1.0f);
  }

  @Override
  public BooleanClause.Occur getOccur()
  {
    return occur;
  }

  public float getBoost()
  {
    return boost;
  }
}