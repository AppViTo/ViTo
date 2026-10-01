import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../bloc/mosaic_bloc.dart';
import '../../domain/entities/mosaic.dart';

class MosaicGridWidget extends StatelessWidget {
  final String eventId;
  
  const MosaicGridWidget({super.key, required this.eventId});
  
  @override
  Widget build(BuildContext context) {
    return BlocBuilder<MosaicBloc, MosaicState>(
      builder: (context, state) {
        if (state is MosaicLoading) {
          return const Center(child: CircularProgressIndicator());
        }
        
        if (state is MosaicGridLoaded || state is MosaicGridUpdated) {
          final grid = state is MosaicGridLoaded 
              ? state.grid 
              : (state as MosaicGridUpdated).grid;
          
          return _buildGrid(context, grid);
        }
        
        return _buildEmptyGrid(context);
      },
    );
  }
  
  Widget _buildGrid(BuildContext context, MosaicGrid grid) {
    return Container(
      padding: const EdgeInsets.all(8),
      child: Column(
        children: [
          // Grid header
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Live Mosaic',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              Text(
                '${grid.totalItems} items',
                style: TextStyle(color: Colors.grey[600]),
              ),
            ],
          ),
          const SizedBox(height: 8),
          
          // Grid view
          Expanded(
            child: GridView.builder(
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 4,
                crossAxisSpacing: 4,
                mainAxisSpacing: 4,
              ),
              itemCount: 16,
              itemBuilder: (context, index) {
                final row = index ~/ 4;
                final col = index % 4;
                
                if (row < grid.grid.length && col < grid.grid[row].length) {
                  final item = grid.grid[row][col];
                  if (item != null) {
                    return _buildGridItem(context, item);
                  }
                }
                
                return _buildEmptyCell(context);
              },
            ),
          ),
        ],
      ),
    );
  }
  
  Widget _buildGridItem(BuildContext context, MosaicItem item) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.blue.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.blue.withValues(alpha: 0.3)),
      ),
      child: Stack(
        children: [
          // Avatar or image
          if (item.mediaUrl != null)
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: Image.network(
                item.mediaUrl!,
                fit: BoxFit.cover,
                width: double.infinity,
                height: double.infinity,
              ),
            )
          else
            Center(
              child: CircleAvatar(
                radius: 20,
                backgroundImage: item.avatar != null 
                    ? NetworkImage(item.avatar!) 
                    : null,
                child: item.avatar == null 
                    ? Text(item.displayName[0].toUpperCase())
                    : null,
              ),
            ),
          
          // Username overlay
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: Container(
              padding: const EdgeInsets.all(4),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    Colors.transparent,
                    Colors.black.withValues(alpha: 0.7),
                  ],
                ),
              ),
              child: Text(
                item.username,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 10,
                  fontWeight: FontWeight.bold,
                ),
                textAlign: TextAlign.center,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ),
          
          // Like count
          if (item.likesCount > 0)
            Positioned(
              top: 4,
              right: 4,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                decoration: BoxDecoration(
                  color: Colors.red,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.favorite, color: Colors.white, size: 10),
                    const SizedBox(width: 2),
                    Text(
                      item.likesCount.toString(),
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 8,
                      ),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
  
  Widget _buildEmptyCell(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.grey[200],
        borderRadius: BorderRadius.circular(8),
      ),
      child: const Center(
        child: Icon(
          Icons.add,
          color: Colors.grey,
          size: 20,
        ),
      ),
    );
  }
  
  Widget _buildEmptyGrid(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.grid_view,
            size: 64,
            color: Colors.grey[400],
          ),
          const SizedBox(height: 16),
          Text(
            'No items yet',
            style: TextStyle(
              fontSize: 18,
              color: Colors.grey[600],
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Be the first to post something!',
            style: TextStyle(
              color: Colors.grey[500],
            ),
          ),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: () {
              // TODO: Initialize grid
            },
            child: const Text('Initialize Grid'),
          ),
        ],
      ),
    );
  }
}
