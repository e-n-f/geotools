/*****************************************************************************/
/**
 *  @file   FastKMeansClustering.cpp
 *  @author Naohisa Sakamoto
 */
/*----------------------------------------------------------------------------
 *
 *  Copyright (c) Visualization Laboratory, Kyoto University.
 *  All rights reserved.
 *  See http://www.viz.media.kyoto-u.ac.jp/kvs/copyright/ for details.
 *
 *  $Id$
 */
/*****************************************************************************/
#include "FastKMeansClustering.h"
#include <kvs/Value>


namespace
{

/*===========================================================================*/
/**
 *  @brief  Returns the row array of the table data specified by the given index.
 *  @param  table [in] pointer to the table data
 *  @param  i [in] row index
 */
/*===========================================================================*/
kvs::ValueArray<kvs::Real32> GetRowArray(
    const kvs::TableObject* table,
    const size_t i )
{
    kvs::ValueArray<kvs::Real32> row( table->ncolumns() );
    const size_t ncolumns = table->ncolumns();
    for ( size_t j = 0; j < ncolumns; j++ )
    {
	row[j] = table->column(j).to<kvs::Real32>(i);
    }

    return row;
}

/*===========================================================================*/
/**
 *  @brief  Returns the distance between the given points.
 *  @param  x0 [in] point 0
 *  @param  x1 [in] point 1
 *  @return distance
 */
/*===========================================================================*/
kvs::Real32 GetEuclideanDistance(
    const kvs::ValueArray<kvs::Real32>& x0,
    const kvs::ValueArray<kvs::Real32>& x1 )
{
    kvs::Real32 distance = 0.0f;
    const size_t nrows = x0.size();
    for ( size_t i = 0; i < nrows; i++ )
    {
	const kvs::Real32 diff = x1[i] - x0[i];
	distance += diff * diff;
    }

    return distance;
}

}

namespace
{

/*===========================================================================*/
/**
 *  @brief  Updates upper and lower bounds and index of the center over all centers.
 *  @param  nclusters [in] number of clusters
 *  @param  xi [in] data point at i-th row in the table data
 *  @param  c [in] set of centers
 *  @param  ai [out] index of the centers for xi
 *  @param  ui [out] upper bound for xi
 *  @param  li [out] lower bound for xi
 */
/*===========================================================================*/
void PointAllCtrs(
    const size_t nclusters,
    const kvs::ValueArray<kvs::Real32>& xi,
    const kvs::ValueArray<kvs::Real32>* c,
    kvs::UInt32& ai,
    kvs::Real32& ui,
    kvs::Real32& li )
{
    // Algorithm 3: POINT-ALL-CTRS( x(i), c, a(i), u(i), l(i) )

    kvs::UInt32 index = 0;
    kvs::Real32 dmin = kvs::Value<kvs::Real32>::Max();
    for ( size_t j = 0; j < nclusters; j++ )
    {
	const kvs::Real32 d = ::GetEuclideanDistance( xi, c[j] );
	if ( d < dmin )
	{
	    dmin = d;
	    index = static_cast<kvs::UInt32>(j);
	}
    }
    ai = index;

    ui = ::GetEuclideanDistance( xi, c[ai] );

    dmin = kvs::Value<kvs::Real32>::Max();
    for ( size_t j = 0; j < nclusters; j++ )
    {
	if ( j != ai )
	{
	    const kvs::Real32 d = ::GetEuclideanDistance( xi, c[j] );
	    dmin = kvs::Math::Min( dmin, d );
	}
    }
    li = dmin;
}

/*===========================================================================*/
/**
 *  @brief  Initializes the upper and lower bounds and the assignments.
 *  @param  nclusters [in] number of clusters
 *  @param  table [in] pointer to the table data
 *  @param  c [in] set of cluster centers
 *  @param  q [out] number of points
 *  @param  cp [out] vector sum of all points
 *  @param  u [out] upper bound
 *  @param  l [out] lower bound
 *  @param  a [out] index of the center
 */
/*===========================================================================*/
void Initialize(
    const size_t nclusters,
    const kvs::TableObject* table,
    const kvs::ValueArray<kvs::Real32>* c,
    kvs::ValueArray<kvs::UInt32>& q,
    kvs::ValueArray<kvs::Real32>* cp,
    kvs::ValueArray<kvs::Real32>& u,
    kvs::ValueArray<kvs::Real32>& l,
    kvs::ValueArray<kvs::UInt32>& a )
{
    // Algorithm 2: INITIALIZE( c, x, q, c', u, l, a )

    for ( size_t j = 0; j < nclusters; j++ )
    {
	q[j] = 0;
	cp[j].fill( 0x00 );
    }

    const size_t nrows = table->nrows();
    const size_t ncolumns = table->ncolumns();
    for ( size_t i = 0; i < nrows; i++ )
    {
	const kvs::ValueArray<kvs::Real32> xi = ::GetRowArray( table, i );
	PointAllCtrs( nclusters, xi, c, a[i], u[i], l[i] );
	q[a[i]] += 1;
	for ( size_t k = 0; k < ncolumns; k++ )
	{
	    cp[a[i]][k] += xi[k];
	}
    }
}

/*===========================================================================*/
/**
 *  @brief  Updates the center locations.
 *  @param  cp [in] set of the vector sum of all points
 *  @param  q [in] array of the number of points
 *  @param  c [out] updated cluster centers
 *  @param  p [out] array of the distance that the cluster center moved
 */
/*===========================================================================*/
void MoveCenters(
    const kvs::ValueArray<kvs::Real32>* cp,
    const kvs::ValueArray<kvs::UInt32>& q,
    kvs::ValueArray<kvs::Real32>* c,
    kvs::ValueArray<kvs::Real32>& p )
{
    // Algorithm 4: MOVE-CENTERS( c', q, c, p )

    const size_t nclusters = q.size();
    for ( size_t j = 0; j < nclusters; j++ )
    {
	kvs::ValueArray<kvs::Real32> cs;
	cs.deepCopy( c[j] );

	const size_t nrows = cp[j].size();
	const kvs::Real32 qj = static_cast<kvs::Real32>( q[j] );
	for ( size_t k = 0; k < nrows; k++ )
	{
	    c[j][k] = cp[j][k] / qj;
	}
	p[j] = ::GetEuclideanDistance( cs, c[j] );
    }
}

/*===========================================================================*/
/**
 *  @brief  Updates the upper and lower bounds.
 *  @param  p [in] array of the distance that the cluster center moved
 *  @param  a [in] array of index of the center
 *  @param  u [out] upper bound
 *  @param  l [out] lower bound
 */
/*===========================================================================*/
void UpdateBounds(
    const kvs::ValueArray<kvs::Real32>& p,
    const kvs::ValueArray<kvs::UInt32>& a,
    kvs::ValueArray<kvs::Real32>& u,
    kvs::ValueArray<kvs::Real32>& l )
{
    // Algorithm 5: UPDATE-BOUNDS( p, a, u, l )

    kvs::Real32 r = 0.0f;
    kvs::Real32 rp = 0.0f;

    kvs::Real32 pmax = kvs::Value<kvs::Real32>::Min();
    const size_t nclusters = p.size();
    for ( size_t j = 0; j < nclusters; j++ )
    {
	if ( p[j] > pmax )
	{
	    pmax = p[j];
	    r = j;
	}
    }

    pmax = kvs::Value<kvs::Real32>::Min();
    for ( size_t j = 0; j < nclusters; j++ )
    {
	if ( j != r )
	{
	    if ( p[j] > pmax )
	    {
		pmax = p[j];
		rp = j;
	    }
	}
    }

    const size_t nrows = u.size();
    for ( size_t i = 0; i < nrows; i++ )
    {
	u[i] += p[a[i]];
	l[i] -= ( r == a[i] ) ? p[rp] : p[r];
    }
}

/*===========================================================================*/
/**
 *  @brief  Updates the number of points specified by the given index.
 *  @param  m [in] index of the center
 *  @param  a [in] array of index of the center
 *  @param  q [in/out] updated the array of the number of points
 */
/*===========================================================================*/
void Update(
    const size_t m,
    const kvs::ValueArray<kvs::UInt32>& a,
    kvs::ValueArray<kvs::UInt32>& q )
{
    size_t counter = 0;
    const size_t nrows = a.size();
    for ( size_t i = 0; i < nrows; i++ )
    {
	if ( a[i] == m ) counter++;
    }

    q[m] = counter;
}

/*===========================================================================*/
/**
 *  @brief  Updates the vector sum of all points specified by the given index.
 *  @param  m [in] index of the center
 *  @param  table [in] pointer to the table data
 *  @param  a [in] array of index of the center
 *  @param  cp [out] set of the vector sum of all points
 */
/*===========================================================================*/
void Update(
    const size_t m,
    const kvs::TableObject* table,
    const kvs::ValueArray<kvs::UInt32>& a,
    kvs::ValueArray<kvs::Real32>* cp )
{
    const size_t nrows = a.size();
    const size_t ncolumns = table->ncolumns();

    cp[m].fill( 0x00 );
    for ( size_t i = 0; i < nrows; i++ )
    {
	if ( a[i] == m )
	{
	    for ( size_t k = 0; k < ncolumns; k++ )
	    {
		cp[m][k] += table->column(k).to<kvs::Real32>(i);
	    }
	}
    }
}

}


namespace pcs
{

/*===========================================================================*/
/**
 *  @brief  Constructs a new FastKMeansClustering class.
 */
/*===========================================================================*/
FastKMeansClustering::FastKMeansClustering():
    m_nclusters( 10 ),
    m_max_iterations( 100 ),
    m_tolerance( 1.e-6 )
{
}

/*===========================================================================*/
/**
 *  @brief  Constructs a new FastKMeansClustering class.
 *  @param  object [in] pointer to the table object
 *  @param  nclusters [in] number of clusters
 */
/*===========================================================================*/
FastKMeansClustering::FastKMeansClustering( const kvs::ObjectBase* object, const size_t nclusters ):
    m_nclusters( nclusters ),
    m_max_iterations( 100 ),
    m_tolerance( 1.e-6 )
{
    this->exec( object );
}

/*===========================================================================*/
/**
 *  @brief  Constructs a new FastKMeansClustering class.
 *  @param  object [in] pointer to the table object
 *  @param  nclusters [in] number of clusters
 *  @param  max_iterations [in] maximum number of iterations
 *  @param  tolerance [in] tolerance for the convergence test
 */
/*===========================================================================*/
FastKMeansClustering::FastKMeansClustering( const kvs::ObjectBase* object, const size_t nclusters, const size_t max_iterations, const double tolerance ):
    m_nclusters( nclusters ),
    m_max_iterations( max_iterations ),
    m_tolerance( tolerance )
{
    this->exec( object );
}

/*===========================================================================*/
/**
 *  @brief  Executes Hamerly's k-means clustering.
 *  @param  object [in] pointer to the table object
 *  @return pointer to the clustered table object
 */
/*===========================================================================*/
FastKMeansClustering::SuperClass* FastKMeansClustering::exec( const kvs::ObjectBase* object )
{
    if ( !object )
    {
	BaseClass::m_is_success = false;
	kvsMessageError("Input object is NULL.");
	return NULL;
    }

    // Input table object.
    const kvs::TableObject* table = static_cast<const kvs::TableObject*>( object );
    const size_t nrows = table->nrows();
    const size_t ncolumns = table->ncolumns();
    const size_t nclusters = m_nclusters;

    // Parameters that relate to cluster centers.
    /*   c:  cluster center
     *   cp: vector sum of all points in the cluster
     *   q:  number of points assigned to the cluster
     *   p:  distance that c last moved
     *   s:  distance from c to its closest other center
     */
    kvs::ValueArray<kvs::Real32>* c = new kvs::ValueArray<kvs::Real32> [ nclusters ];
    kvs::ValueArray<kvs::Real32>* cp = new kvs::ValueArray<kvs::Real32> [ nclusters ];
    kvs::ValueArray<kvs::UInt32> q( nclusters );
    kvs::ValueArray<kvs::Real32> p( nclusters );
    kvs::ValueArray<kvs::Real32> s( nclusters );

    for ( size_t j = 0; j < nclusters; j++ )
    {
	c[j].allocate( ncolumns );
	cp[j].allocate( ncolumns );
    }

    // Parameters that relate to data points.
    /*   a:  index of the center to which the data point x is assigned
     *   u:  upper bound on the distance between the data point x and
     *       its assigned center c(a)
     *   l:  lower bound on the distance between the data point x and
     *       its second closest center (the closest center to the data
     *       point that is not c(a))
     */
    kvs::ValueArray<kvs::UInt32> a( nrows );
    kvs::ValueArray<kvs::Real32> u( nrows );
    kvs::ValueArray<kvs::Real32> l( nrows );

    // Assign initial centers.
    for ( size_t j = 0; j < nclusters; j++ )
    {
	const kvs::UInt32 index = nrows * m_random.rand();
	c[j] = ::GetRowArray( table, index );
    }

    // Initialize.
    ::Initialize( nclusters, table, c, q, cp, u, l, a );

    // Cluster IDs.
    kvs::ValueArray<kvs::UInt32> IDs;

    // Clustering.
    bool converged = false;
    size_t counter = 0;
    while ( !converged )
    {
	// Update s.
	for ( size_t j = 0; j < nclusters; j++ )
	{
	    kvs::Real32 dmin = kvs::Value<kvs::Real32>::Max();
	    for ( size_t jp = 0; jp < nclusters; jp++ )
	    {
		if ( jp != j )
		{
		    const kvs::Real32 d = ::GetEuclideanDistance( c[jp], c[j] );
		    dmin = kvs::Math::Min( dmin, d );
		}
	    }
	    s[j] = dmin;
	}

	for ( size_t i = 0; i < nrows; i++ )
	{
	    const kvs::Real32 m = kvs::Math::Max( s[a[i]] * 0.5f, l[i] );
	    if ( u[i] > m ) // First bound test.
	    {
		// Tighten upper bound.
		const kvs::ValueArray<kvs::Real32> xi = ::GetRowArray( table, i );
		u[i] = ::GetEuclideanDistance( xi, c[a[i]] );
		if ( u[i] > m ) // Second bound test.
		{
		    const kvs::UInt32 ap = a[i];
		    ::PointAllCtrs( nclusters, xi, c, a[i], u[i], l[i] );
		    if ( ap != a[i] )
		    {
			::Update( ap, a, q );
			::Update( a[i], a, q );
			::Update( ap, table, a, cp );
			::Update( a[i], table, a, cp );
		    }
		}
	    }
	}

	::MoveCenters( cp, q, c, p );
	::UpdateBounds( p, a, u, l );

	// Update cluster IDs.
	IDs = a;

	// Convergence test.
	converged = true;
	for ( size_t j = 0; j < nclusters; j++ )
	{
	    if ( !( p[j] < m_tolerance ) ) { converged = false; break; }
	}

	if ( counter++ > m_max_iterations ) break;
    }

    delete [] c;
    delete [] cp;

    // Set the results to the output table object.
    for ( size_t i = 0; i < ncolumns; i++ )
    {
	const std::string label = table->label(i);
	const kvs::AnyValueArray& column = table->column(i);
	SuperClass::addColumn( column, label );
    }
    SuperClass::addColumn( IDs, "Cluster ID" );

    return this;
}

void FastKMeansClustering::setSeed( const size_t seed )
{
    m_random.setSeed( seed );
}

/*===========================================================================*/
/**
 *  @brief  Sets a number of clusters.
 *  @param  nclusters [in] number of clusters
 */
/*===========================================================================*/
void FastKMeansClustering::setNumberOfClusters( const size_t nclusters )
{
    m_nclusters = nclusters;
}

/*===========================================================================*/
/**
 *  @brief  Sets a maximum number of interations.
 *  @param  max_iterations [in] maximum number of iterations
 */
/*===========================================================================*/
void FastKMeansClustering::setMaxInterations( const size_t max_iterations )
{
    m_max_iterations = max_iterations;
}

/*===========================================================================*/
/**
 *  @brief  Sets a tolerance for the convergence test.
 *  @param  tolerance [in] tolerance which can be assumed zero
 */
/*===========================================================================*/
void FastKMeansClustering::setTolerance( const double tolerance )
{
    m_tolerance = tolerance;
}

} // end of namespace pcs
