import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:intl/intl.dart';
import 'package:share_plus/share_plus.dart';

import '../../../../core/theme/app_colors.dart';
import '../../domain/entities/invite_link.dart';
import '../bloc/invite_links_cubit.dart';

/// Texto que acompaña al enlace al compartirlo (WhatsApp u otra app).
String inviteShareText(String eventTitle, String url) =>
    'Te invito a «$eventTitle» en ViTo: $url';

/// Enlaces de invitación del organizador (T042): crear, copiar, compartir
/// por WhatsApp y revocar, con usos y vencimiento.
class InviteLinksPage extends StatefulWidget {
  final String eventTitle;

  /// Por defecto, la hoja de compartir del sistema. Las pruebas pasan otra.
  final Future<void> Function(String text)? share;

  const InviteLinksPage({super.key, required this.eventTitle, this.share});

  @override
  State<InviteLinksPage> createState() => _InviteLinksPageState();
}

class _InviteLinksPageState extends State<InviteLinksPage> {
  @override
  void initState() {
    super.initState();
    context.read<InviteLinksCubit>().load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Enlaces de invitación')),
      body: BlocConsumer<InviteLinksCubit, InviteLinksState>(
        listenWhen: (previous, current) =>
            (current.error != null && current.status != InviteLinksStatus.failure) ||
            (current.created != null && current.created != previous.created),
        listener: (context, state) {
          final messenger = ScaffoldMessenger.of(context);
          if (state.error != null) {
            messenger.showSnackBar(SnackBar(content: Text(state.error!)));
          } else if (state.created != null) {
            messenger.showSnackBar(
              const SnackBar(content: Text('Enlace creado: ya puedes compartirlo')),
            );
          }
        },
        builder: (context, state) {
          switch (state.status) {
            case InviteLinksStatus.loading:
              return const Center(child: CircularProgressIndicator());
            case InviteLinksStatus.failure:
              return Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        state.error ?? 'No pudimos cargar los enlaces',
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 16),
                      ElevatedButton(
                        onPressed: () => context.read<InviteLinksCubit>().load(),
                        child: const Text('Reintentar'),
                      ),
                    ],
                  ),
                ),
              );
            case InviteLinksStatus.ready:
              return _buildList(context, state);
          }
        },
      ),
    );
  }

  Widget _buildList(BuildContext context, InviteLinksState state) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const Text(
          'Quien tenga el enlace puede ver el evento sin la dirección exacta y '
          'unirse con su cuenta. Caduca en 7 días.',
          style: TextStyle(color: AppColors.textSecondary),
        ),
        const SizedBox(height: 16),
        ElevatedButton.icon(
          onPressed:
              state.busy ? null : () => context.read<InviteLinksCubit>().create(),
          icon: const Icon(Icons.add_link),
          label: const Text('Crear enlace de invitación'),
        ),
        const SizedBox(height: 24),
        if (state.links.isEmpty)
          const Text(
            'Aún no has creado enlaces para este evento',
            textAlign: TextAlign.center,
            style: TextStyle(color: AppColors.textSecondary),
          )
        else
          for (final link in state.links)
            _InviteLinkCard(
              link: link,
              busy: state.busy,
              onCopy: () => _copy(link),
              onShare: () => _share(link),
              onRevoke: () => _confirmRevoke(link),
            ),
      ],
    );
  }

  Future<void> _copy(InviteLink link) async {
    await Clipboard.setData(ClipboardData(text: link.url));
    if (!mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(const SnackBar(content: Text('Enlace copiado')));
  }

  Future<void> _share(InviteLink link) {
    final text = inviteShareText(widget.eventTitle, link.url);
    final share = widget.share;
    if (share != null) return share(text);
    return SharePlus.instance.share(ShareParams(text: text));
  }

  Future<void> _confirmRevoke(InviteLink link) async {
    final cubit = context.read<InviteLinksCubit>();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('¿Revocar el enlace?'),
        content: const Text(
          'Nadie más podrá unirse con este enlace. Quienes ya se unieron '
          'siguen invitados.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Cancelar'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Revocar'),
          ),
        ],
      ),
    );
    if (confirmed == true) await cubit.revoke(link.id);
  }
}

class _InviteLinkCard extends StatelessWidget {
  final InviteLink link;
  final bool busy;
  final VoidCallback onCopy;
  final VoidCallback onShare;
  final VoidCallback onRevoke;

  const _InviteLinkCard({
    required this.link,
    required this.busy,
    required this.onCopy,
    required this.onShare,
    required this.onRevoke,
  });

  @override
  Widget build(BuildContext context) {
    final uses = link.maxUses == null
        ? '${link.uses} ${link.uses == 1 ? 'uso' : 'usos'}'
        : '${link.uses} de ${link.maxUses} usos';
    final expires =
        DateFormat("d 'de' MMMM, h:mm a", 'es').format(link.expiresAt);

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    link.url,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                ),
                const SizedBox(width: 8),
                _StatusChip(link: link),
              ],
            ),
            const SizedBox(height: 4),
            Text(
              link.isActive || link.isExhausted
                  ? '$uses · vence el $expires'
                  : uses,
              style: const TextStyle(color: AppColors.textSecondary),
            ),
            if (link.isActive) ...[
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                children: [
                  TextButton.icon(
                    onPressed: onCopy,
                    icon: const Icon(Icons.copy, size: 18),
                    label: const Text('Copiar'),
                  ),
                  TextButton.icon(
                    onPressed: onShare,
                    icon: const Icon(Icons.chat_bubble_outline, size: 18),
                    label: const Text('Enviar por WhatsApp'),
                  ),
                  TextButton.icon(
                    onPressed: busy ? null : onRevoke,
                    icon: const Icon(Icons.link_off, size: 18),
                    label: const Text('Revocar'),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  final InviteLink link;

  const _StatusChip({required this.link});

  @override
  Widget build(BuildContext context) {
    final (text, color) = link.revoked
        ? ('Revocado', AppColors.textMuted)
        : link.isExpired
            ? ('Vencido', AppColors.textMuted)
            : link.isExhausted
                ? ('Sin usos', AppColors.warning)
                : ('Activo', AppColors.success);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(text, style: TextStyle(color: color, fontSize: 12)),
    );
  }
}
