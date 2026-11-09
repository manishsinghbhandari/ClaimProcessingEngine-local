package io.coredge.health.claims.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRequest {
  private String username;
  private String firstName;
  private String lastName;
  private String email;
  private String password;
  private String mobileNo;
  
  // Wallet-related fields
  private String walletId; // Optional pre-existing wallet ID
  private boolean createWallet = true; // Flag to control wallet creation (default: true)
  private String walletType; // Optional wallet type specification
}
