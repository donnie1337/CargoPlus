# CargoPlus

O CargoPlus é o plugin que cuida dos cargos, hierarquia e permissões do meu servidor.

A ideia é deixar os cargos centralizados em um lugar só, para os outros plugins conseguirem usar as mesmas informações de cargo, prefixo e cor.

## O que tem no plugin

### Cargos

- Cargos configuráveis em uma hierarquia.
- Promoção para o próximo cargo.
- Definição manual de cargo.
- Remoção de cargo personalizado.
- Aplicação das permissões do cargo ao jogador.
- Dados dos jogadores salvos para não perder as informações depois do restart.

### Permissões

O CargoPlus controla as permissões relacionadas aos cargos e também possui permissões próprias para administração.

Principais permissões administrativas:

- `cargoplus.promover`
- `cargoplus.setcargo`
- `cargoplus.removercargo`
- `cargoplus.admin`

O `/setcargo` é um comando administrativo e não é um comando para jogador comum.

### DEV

O cargo DEV possui uma animação própria no TAB e acima da cabeça do jogador.

Essa animação não precisa aparecer no chat. O ChatPlus usa o prefixo normal do cargo para manter o chat estável, enquanto o TAB e o nametag podem usar a animação.

### Integração com o ChatPlus

O CargoPlus fornece para o ChatPlus as informações de cargo, prefixo e cores usadas no chat.

A ideia é que eu consiga mudar o cargo de um jogador no CargoPlus e os outros plugins já consigam pegar essa informação automaticamente.

### Integração com LoginPlus

O CargoPlus usa o LoginPlus para saber se o jogador já está autenticado. Jogadores que ainda não fizeram login não recebem as permissões do cargo.

### API

O plugin disponibiliza uma API para os outros plugins do servidor consultarem cargos, prefixos, cores e permissões.

### Salvamento

- Dados salvos em arquivo.
- Salvamento assíncrono.
- Proteção contra gravações concorrentes.
- Salvamento seguro ao desligar o servidor.
- Configuração podendo ser recarregada sem precisar reiniciar.

## Comandos

| Comando | O que faz |
|---|---|
| `/promover <jogador>` | Promove o jogador para o próximo cargo. |
| `/setcargo <jogador> <cargo>` | Define o cargo do jogador. |
| `/removercargo <jogador>` | Remove o cargo personalizado. |
| `/cargo reload` | Recarrega o CargoPlus. |

## Permissões

| Permissão | O que faz | Padrão |
|---|---|---|
| `cargoplus.promover` | Promover jogadores | `false` |
| `cargoplus.setcargo` | Definir cargos | `false` |
| `cargoplus.removercargo` | Remover cargos | `false` |
| `cargoplus.admin` | Administrar o CargoPlus | `false` |

## Integrações

- **LoginPlus:** verifica a autenticação do jogador.
- **ChatPlus:** usa os dados de cargo e cores no chat.

## Plataforma

- Java 26
- Spigot API 26.2
- Maven

## Build

```bash
mvn -B clean package
```

O projeto possui build automático pelo GitHub Actions.

## Status

O CargoPlus está em desenvolvimento e é a base do sistema de cargos do meu servidor. A ideia é manter os cargos organizados e fazer com que os outros plugins consigam conversar com ele sem precisar duplicar essas informações.
